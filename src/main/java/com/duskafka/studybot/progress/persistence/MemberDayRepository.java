package com.duskafka.studybot.progress.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 멤버 일 행을 만들고 잠그고 센 수를 고친다 (5.5 '만들 것', S09 만들 것 2·4). 현황을 위해 하루치를 읽는다 (S10).
 *
 * <p>쓰기와 잠금은 엔티티를 거치지 않고 네이티브 쿼리만 쓴다. open-in-view로 요청 내내 열려 있는 EntityManager에 앞서 읽은 엔티티가 남아 있으면,
 * 잠금 쿼리가 행을 잠그더라도 그 엔티티를 DB 값으로 다시 채우지 않고 그대로 돌려준다 (S07 PR #14 리뷰,
 * {@code GithubInstallationRepository#lockCurrentState}). 모두 쓰기 트랜잭션 안에서 부른다.
 */
public interface MemberDayRepository extends JpaRepository<MemberDay, Long> {

	/**
	 * 멤버 일 행이 없으면 만든다 (센 수 0, 미판정). 이미 있으면 아무것도 하지 않는다. 동시에 둘이 만들어도 UNIQUE 위반 없이 한 행만 남는다
	 * (나중 트랜잭션은 앞 트랜잭션이 끝날 때까지 기다린다). 다음에 {@link #lock}으로 잠근다.
	 *
	 * @param seasonId 그날을 담은 기수 행. 기수 밖인 날이면 {@code null}
	 * @return 넣은 행 수
	 */
	@Modifying
	@Query(value = """
			insert into studybot.member_days (study_id, user_id, study_day, season_id, solved_count, version)
			values (:studyId, :userId, :studyDay, cast(:seasonId as bigint), 0, 0)
			on conflict (study_id, user_id, study_day) do nothing
			""", nativeQuery = true)
	int insertIfAbsent(@Param("studyId") long studyId, @Param("userId") long userId,
			@Param("studyDay") LocalDate studyDay, @Param("seasonId") @Nullable Long seasonId);

	/**
	 * 멤버 일 행을 잠그고 지금 값을 읽는다 ({@code SELECT ... FOR UPDATE}, 5.5). 트랜잭션이 끝날 때까지 같은 행을 잠그는 배정·판정은
	 * 기다린다. 그래서 '미판정·면제권 없음·목표 미달'을 확인하고 배정하는 사이에 다른 배정이나 판정이 끼어들지 못한다.
	 */
	@Query(value = """
			select id as "id", solved_count as "solvedCount", (exemption_used_at is not null) as "exemptionUsed",
				(result is not null) as "judged", coalesce(result = 'FAIL', false) as "failed"
			from studybot.member_days
			where study_id = :studyId and user_id = :userId and study_day = :studyDay
			for update
			""", nativeQuery = true)
	Optional<LockedMemberDay> lock(@Param("studyId") long studyId, @Param("userId") long userId,
			@Param("studyDay") LocalDate studyDay);

	/**
	 * 센 수를 그 날의 {@code study_day_solves} 행 수로 다시 센다 (S09 만들 것 4). 낙관적 잠금 값도 올린다. 행을 잠근 뒤에 부른다.
	 *
	 * @return 고친 행 수
	 */
	@Modifying
	@Query(value = """
			update studybot.member_days m
			set solved_count = (select count(*) from studybot.study_day_solves s
					where s.study_id = m.study_id and s.user_id = m.user_id and s.study_day = m.study_day),
				version = m.version + 1
			where m.id = :id
			""", nativeQuery = true)
	int recountSolved(@Param("id") long id);

	/**
	 * 스터디 하루의 멤버 일 행들 (S10 현황). 조회 전용 DTO로 읽어 엔티티를 영속성 컨텍스트에 올리지 않는다. 인덱스
	 * {@code member_days_study_day_ix}를 쓴다. 잠그지 않는다.
	 */
	@Query("""
			select new com.duskafka.studybot.progress.persistence.MemberDayRow(m.userId, m.solvedCount, m.exemptionUsedAt,
				m.result)
			from MemberDay m
			where m.studyId = :studyId and m.studyDay = :studyDay
			""")
	List<MemberDayRow> findDayRows(@Param("studyId") long studyId, @Param("studyDay") LocalDate studyDay);

	/**
	 * 면제권을 쓴 시각을 남긴다 (5.7, S11 만들 것 5). 이미 썼으면 바꾸지 않는다. 행을 잠근 뒤에 부른다.
	 *
	 * @return 고친 행 수
	 */
	@Modifying
	@Query(value = """
			update studybot.member_days set exemption_used_at = :usedAt, version = version + 1
			where id = :id and exemption_used_at is null
			""", nativeQuery = true)
	int markExemptionUsed(@Param("id") long id, @Param("usedAt") Instant usedAt);

	/**
	 * 면제권을 취소한다 (R8, S11 만들 것 5). 쓰지 않았으면 바꾸지 않는다. 행을 잠근 뒤에 부른다.
	 *
	 * @return 고친 행 수
	 */
	@Modifying
	@Query(value = """
			update studybot.member_days set exemption_used_at = null, version = version + 1
			where id = :id and exemption_used_at is not null
			""", nativeQuery = true)
	int clearExemption(@Param("id") long id);

	/**
	 * 한 멤버가 기간(이번 기수) 안에 쓴 면제권 수 (pseudocode '면제권 사용'). 성공으로 판정된 날은 면제권을 돌려받았으니 세지 않는다 (R8).
	 * 판정 전인 날과 면제(면제권)로 판정된 날을 센다. 기간은 기수 행의 날짜다 (5.13 '주의할 것': 경계는 날짜로 본다).
	 */
	@Query(value = """
			select count(*) from studybot.member_days
			where study_id = :studyId and user_id = :userId and study_day between :fromDay and :toDay
				and exemption_used_at is not null and result is distinct from 'SUCCESS'
			""", nativeQuery = true)
	long countUsedExemptions(@Param("studyId") long studyId, @Param("userId") long userId,
			@Param("fromDay") LocalDate fromDay, @Param("toDay") LocalDate toDay);

	/** {@link #countUsedExemptions}를 스터디의 멤버마다 한 번에 센다 (현황의 남은 면제권, S11 만들 것 6). 쓴 적 없는 멤버는 빠진다. */
	@Query(value = """
			select user_id as "userId", count(*) as "count" from studybot.member_days
			where study_id = :studyId and study_day between :fromDay and :toDay
				and exemption_used_at is not null and result is distinct from 'SUCCESS'
			group by user_id
			""", nativeQuery = true)
	List<UserCount> countUsedExemptionsByUser(@Param("studyId") long studyId, @Param("fromDay") LocalDate fromDay,
			@Param("toDay") LocalDate toDay);

	/**
	 * 판정 결과를 남긴다 (5.10, S12 만들 것 4). 결과가 이미 있으면 바꾸지 않는다 (다시 돌려도 안전, 5.10 '주의할 것'). 그날의 기수 행이 비어
	 * 있으면 채운다 (다음 기수 행이 생기기 전에 만든 행). 행을 잠근 뒤에 부른다.
	 *
	 * @param result 판정 결과 ({@code JudgmentResult} 이름)
	 * @param resultSource 결과를 정한 곳 (판정 배치는 {@code BATCH})
	 * @param ruleId 판정에 쓴 규칙 버전
	 * @param seasonId 그날을 담은 기수 행
	 * @return 고친 행 수
	 */
	@Modifying
	@Query(value = """
			update studybot.member_days
			set result = :result, result_source = :resultSource, judged_at = :judgedAt, rule_id = :ruleId,
				season_id = coalesce(season_id, cast(:seasonId as bigint)), version = version + 1
			where id = :id and result is null
			""", nativeQuery = true)
	int recordJudgment(@Param("id") long id, @Param("result") String result, @Param("resultSource") String resultSource,
			@Param("judgedAt") Instant judgedAt, @Param("ruleId") long ruleId, @Param("seasonId") @Nullable Long seasonId);

	/**
	 * 기간(이번 기수) 안에 실패로 판정된 날 수를 멤버마다 센다 (현황의 P1 정산 표시, S12 만들 것 7). 실패가 없는 멤버는 빠진다. 기간은 기수 행의
	 * 날짜다.
	 */
	@Query(value = """
			select user_id as "userId", count(*) as "count" from studybot.member_days
			where study_id = :studyId and study_day between :fromDay and :toDay and result = 'FAIL'
			group by user_id
			""", nativeQuery = true)
	List<UserCount> countFailuresByUser(@Param("studyId") long studyId, @Param("fromDay") LocalDate fromDay,
			@Param("toDay") LocalDate toDay);

	/** 사용자마다 센 수. */
	interface UserCount {

		long getUserId();

		long getCount();

	}

	/** 잠그고 읽은 멤버 일의 지금 값 중 배정에 필요한 것. */
	interface LockedMemberDay {

		long getId();

		int getSolvedCount();

		boolean getExemptionUsed();

		boolean getJudged();

		/** 실패로 판정됐는지 ({@code result = 'FAIL'}). 확인 대기 지정(S10)이 본다. */
		boolean getFailed();

	}

}
