package com.duskafka.studybot.study.service;

import com.duskafka.studybot.common.error.ConflictException;
import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.common.time.StudyCalendar;
import com.duskafka.studybot.study.StudyAccess;
import com.duskafka.studybot.study.StudyRulesChanged;
import com.duskafka.studybot.study.persistence.MembershipRepository;
import com.duskafka.studybot.study.persistence.Season;
import com.duskafka.studybot.study.persistence.SeasonRepository;
import com.duskafka.studybot.study.persistence.Study;
import com.duskafka.studybot.study.persistence.StudyRepository;
import com.duskafka.studybot.study.persistence.StudyRule;
import com.duskafka.studybot.study.persistence.StudyRuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 규칙 변경 (S05 만들 것 4, R2, OD-07, OD-08, T11). 권한은 방장이다 (api.md 4.5).
 *
 * <p>요청은 원하는 규칙 전체와, 그 값을 채울 때 출발한 버전 ID다 (사람이 정함, 2026-11-24 "규칙 전체를 받음", "기준 버전 ID를
 * 받음"). 출발한 버전이 마지막 버전(적용 시작일이 가장 늦은 버전)이 아니면 409다. 예정 버전은 바꿀 때마다 새 ID로 다시 만들어지니
 * ID가 규칙의 리비전 역할을 한다. 아직 적용되지 않은 예정 버전을 지우고 원하는 규칙에
 * 맞춰 다시 만든다. 이미 적용된 버전(오늘까지)은 고치지 않는다 (과거 판정에 소급하지 않음).
 *
 * <ul>
 * <li><b>1기 시작 전</b>(오늘 &lt; 1기 시작일): 아직 판정한 날이 없어 모든 항목을 1기 시작일부터 적용한다. 1기 끝 날짜도 새 기수
 * 길이로 다시 센다 (사람이 정함, 2026-11-24 "모두 1기 시작일부터"). 다만 기준 시각을 앞당겨 1기 첫날이 이미 시작된 것이
 * 되면 409로 거절한다. 그 사이 가입한 멤버의 '가입 다음 날부터 판정'(R14)이 깨지기 때문이다 (사람이 정함, 2026-11-24 "거절 (409)").</li>
 * <li><b>기수 중</b>: 다음 스터디 일 항목은 다음 스터디 일부터, 다음 기수 항목은 다음 기수 시작일(오늘이 속한 기수의 끝 + 1)부터
 * ({@link RuleSettings}). 오늘이 기수 마지막 날이면 두 날이 같아 버전 하나다. 지금 규칙과 같은 종류는 버전을 만들지 않으니,
 * 지금 규칙과 같게 보내면 예정된 변경이 없어진다.</li>
 * </ul>
 *
 * <p>기수 중 변경이면 이미 만든 다음 기수 행의 끝 날짜도 다시 센다 (S12, 5.13 '주의할 것').
 *
 * <p>버전 행은 그날부터의 규칙 전체를 담는다(스냅샷). 테이블은 그대로다 ({@code (study_id, effective_from)} UNIQUE).
 * 같은 스터디의 규칙 변경·수락·위임은 스터디 행 잠금으로 차례로 실행된다 (결정 제안서 006).
 *
 * <p>예정된 규칙이 실제로 달라졌거나(바꾸기 전과 적용 예정 구간의 적용 시작일·규칙 값이 다르면) 예정된 변경을 취소했으면 같은 트랜잭션 안에서
 * {@link StudyRulesChanged}를 발행한다. notification이 같은 트랜잭션에서 멤버에게 알림을 넣는다 (S13 만들 것 4, 알려진 충돌 5번, 사람이 정함 2026-11-27 "Listener in the same transaction (Recommended)").
 */
@Service
@RequiredArgsConstructor
public class StudyRuleService {

	private final StudyAccess studyAccess;

	private final StudyRepository studyRepository;

	private final StudyRuleRepository ruleRepository;

	private final SeasonRepository seasonRepository;

	private final MembershipRepository membershipRepository;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	/**
	 * @param basedOnRuleId 요청이 출발한 규칙 버전 ID. 마지막 버전이어야 한다
	 * @param desired 원하는 규칙 전체
	 */
	@Transactional
	public void change(long studyId, long actorUserId, long basedOnRuleId, RuleSettings desired) {
		Study study = this.studyRepository.findByIdForUpdate(studyId)
			.orElseThrow(() -> new NotFoundException("스터디를 찾을 수 없어요."));
		this.studyAccess.requireOwner(studyId, actorUserId);
		List<StudyRule> rules = this.ruleRepository.findByStudyIdOrderByEffectiveFromAsc(studyId);
		if (rules.getLast().getId() != basedOnRuleId) {
			throw new ConflictException("규칙이 그사이 바뀌었거나 예정된 변경을 반영하지 않은 값이에요. 최신 규칙(예정된 변경 포함)을 다시 불러와 주세요.");
		}
		List<Season> seasons = this.seasonRepository.findByStudyIdOrderBySeqAsc(studyId);
		Instant now = this.clock.instant();
		LocalDate today = StudyTimeline.calendar(rules, study.zoneId()).studyDayOf(now);
		Season first = seasons.getFirst();

		if (today.isBefore(first.getStartDay())) {
			UpcomingVersions replaced = UpcomingVersions.from(rules, first.getStartDay());
			StudyRule saved = changeBeforeFirstSeason(studyId, study, first, desired, now);
			publishChanged(study, actorUserId, replaced, List.of(saved));
			return;
		}

		LocalDate nextDay = today.plusDays(1);
		LocalDate nextSeasonStart = StudyTimeline.nextSeasonStart(seasons, rules, today);
		RuleSettings current = RuleSettings.of(StudyTimeline.ruleOn(rules, today));
		UpcomingVersions replaced = UpcomingVersions.from(rules, nextDay);
		this.ruleRepository.deleteFrom(studyId, nextDay);
		List<StudyRule> saved = new ArrayList<>();
		if (nextSeasonStart.equals(nextDay)) {
			if (!desired.equals(current)) {
				saved.add(this.ruleRepository.save(desired.toVersion(studyId, nextDay, now)));
			}
		}
		else {
			if (!desired.sameNextDayItems(current)) {
				saved.add(this.ruleRepository.save(desired.withNextSeasonItemsOf(current).toVersion(studyId, nextDay, now)));
			}
			if (!desired.sameNextSeasonItems(current)) {
				saved.add(this.ruleRepository.save(desired.toVersion(studyId, nextSeasonStart, now)));
			}
		}
		rescheduleNextSeason(studyId, today, nextSeasonStart, desired);
		publishChanged(study, actorUserId, replaced, saved);
	}

	/**
	 * 예정된 규칙이 실제로 달라졌거나 취소됐으면 알린다 (S13 만들 것 4). 받는 사람은 탈퇴하지 않은 멤버 중 바꾼 방장을 뺀 사람이다. 받는 쪽이 같은
	 * 트랜잭션에서 알림 행을 넣는다.
	 *
	 * <ul>
	 * <li>예정 버전은 바꿀 때마다 지우고 새 ID로 다시 만든다. 그래서 같은 예정 변경을 다시 보내거나 1기 시작 전에 지금 규칙과 같게 보내도 새 행이
	 * 생긴다. 이때는 바꾸기 전과 (적용 시작일, 규칙 값)이 모두 같으니 알리지 않는다.</li>
	 * <li>예정 버전이 있었는데 새로 만든 것이 없으면(지금 규칙과 같게 보냄) 예정된 변경을 취소한 것이다. 취소도 알린다 (사람이 정함, 2026-11-28
	 * "예정된 규칙 변경을 취소만 할 때도 알리자"). 예정 버전도 없었으면 아무것도 바뀌지 않았으니 알리지 않는다.</li>
	 * </ul>
	 *
	 * @param replaced 이번에 지운 예정 버전들
	 * @param saved 이번에 만든 버전들 (적용 시작일 순서)
	 */
	private void publishChanged(Study study, long actorUserId, UpcomingVersions replaced, List<StudyRule> saved) {
		boolean cancelled = saved.isEmpty();
		if (cancelled ? replaced.isEmpty() : replaced.terms().equals(saved.stream().map(VersionTerms::of).toList())) {
			return;
		}
		List<Long> recipients = this.membershipRepository.findActiveUserIds(study.getId())
			.stream()
			.filter(userId -> userId != actorUserId)
			.toList();
		long ruleId = cancelled ? replaced.ruleIds().getLast() : saved.getLast().getId();
		LocalDate effectiveFrom = cancelled ? replaced.terms().getFirst().effectiveFrom()
				: saved.getFirst().getEffectiveFrom();
		this.events.publishEvent(new StudyRulesChanged(study.getId(), study.getName(), ruleId, effectiveFrom, cancelled,
				actorUserId, recipients));
	}

	/**
	 * 이미 만든 다음 기수 행(S12 판정 배치가 이번 기수가 시작될 때 만듦)의 끝 날짜를 다음 기수 시작일에 적용되는 기수 길이로 다시 센다 (S12 만들 것 6
	 * '예정 행의 끝 날짜', R2, 5.13). 변경이든 취소든 위 버전 저장 뒤 그 시작일의 기수 길이는 원하는 규칙의 길이다: 다음 기수 항목이 다르면 그
	 * 시작일에 원하는 규칙 버전을 두고, 같으면 지금 규칙의 길이가 곧 원하는 길이다. 진행 중인 기수의 끝 날짜는 바꾸지 않는다 (R2).
	 */
	private void rescheduleNextSeason(long studyId, LocalDate today, LocalDate nextSeasonStart, RuleSettings desired) {
		this.seasonRepository.rescheduleUpcoming(studyId, nextSeasonStart, today,
				StudyCalendar.seasonEndDay(nextSeasonStart, desired.seasonLengthDays()));
	}

	private StudyRule changeBeforeFirstSeason(long studyId, Study study, Season first, RuleSettings desired, Instant now) {
		LocalDate start = first.getStartDay();
		// 바꾼 뒤에는 1기 시작일이 첫 버전이라, 첫날은 새 규칙의 기준 시각에 시작한다 (RuleVersionCalendar).
		Instant newStart = StudyCalendar.of(desired.toTimeRule(study.zoneId())).boundaryOf(start);
		if (!now.isBefore(newStart)) {
			throw new ConflictException("이 기준 시각으로 바꾸면 1기 첫날이 이미 시작돼요. 1기가 시작한 뒤에 바꾸면 다음 기수부터 적용돼요.");
		}
		this.ruleRepository.deleteFrom(studyId, start);
		StudyRule saved = this.ruleRepository.save(desired.toVersion(studyId, start, now));
		int rescheduled = this.seasonRepository.rescheduleEnd(first.getId(),
				StudyCalendar.seasonEndDay(start, desired.seasonLengthDays()));
		if (rescheduled != 1) {
			// 시작 전인데 1기가 SCHEDULED가 아니면 기수 상태가 날짜와 어긋난 것이다. 규칙과 기수 끝 날짜가 어긋나게 두지 않는다.
			throw new IllegalStateException("시작 전 1기의 끝 날짜를 바꾸지 못했어요: season=" + first.getId());
		}
		return saved;
	}

	/**
	 * 바꾸기 전, 이 날짜부터 적용하는 버전들 (적용 시작일 순서). 지우기 전에 값을 잡아 둔다.
	 *
	 * @param terms 버전마다 적용 시작일과 규칙 값
	 * @param ruleIds 버전 ID (같은 순서)
	 */
	private record UpcomingVersions(List<VersionTerms> terms, List<Long> ruleIds) {

		static UpcomingVersions from(List<StudyRule> rules, LocalDate from) {
			List<StudyRule> upcoming = rules.stream().filter(rule -> !rule.getEffectiveFrom().isBefore(from)).toList();
			return new UpcomingVersions(upcoming.stream().map(VersionTerms::of).toList(),
					upcoming.stream().map(StudyRule::getId).toList());
		}

		boolean isEmpty() {
			return this.terms.isEmpty();
		}

	}

	/** 규칙 버전 하나의 적용 시작일과 값. ID와 만든 시각은 뺀다. */
	private record VersionTerms(LocalDate effectiveFrom, RuleSettings settings) {

		static VersionTerms of(StudyRule rule) {
			return new VersionTerms(rule.getEffectiveFrom(), RuleSettings.of(rule));
		}

	}

}
