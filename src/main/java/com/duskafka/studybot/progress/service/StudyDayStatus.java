package com.duskafka.studybot.progress.service;

import com.duskafka.studybot.progress.JudgmentResult;
import com.duskafka.studybot.progress.MemberDayProgress;
import com.duskafka.studybot.progress.MemberDayStatus;
import com.duskafka.studybot.study.MembershipRole;
import com.duskafka.studybot.study.StudyDayContext;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 스터디 일 하루의 현황 (S10 만들 것 1·2). {@link StudyDayStatusService}가 만든다.
 *
 * @param day 그날의 규칙·시각·기수
 * @param members 그날 멤버였던 사람들의 상태 (가입 순서)
 * @param recentSolves 그날 센 풀이 중 최근 것 (수신 시각이 늦은 것부터, {@link StudyDayStatusService#RECENT_SOLVES_LIMIT}개까지)
 */
public record StudyDayStatus(StudyDayContext day, List<MemberStatus> members, List<RecentSolve> recentSolves) {

	public StudyDayStatus {
		Objects.requireNonNull(day, "day");
		members = List.copyOf(members);
		recentSolves = List.copyOf(recentSolves);
	}

	/** 판정 대상인 멤버 중 그 상태인 사람 수. 판정하지 않는 멤버({@link MemberDayStatus#NOT_JUDGED})는 세지 않는다. */
	public long count(MemberDayStatus status) {
		return this.members.stream().filter(member -> member.progress().status() == status).count();
	}

	/**
	 * 멤버 하나의 그날 상태.
	 *
	 * @param userId 사용자 ID
	 * @param login GitHub 로그인 이름
	 * @param avatarUrl 아바타 주소
	 * @param role 역할
	 * @param progress 상태와 면제 까닭 (지금 센 수로 정함)
	 * @param exemptionUsed 그날 면제권을 썼는지 (목표를 채워도 표시, 5.7)
	 * @param solvedCount 그날 센 문제 수
	 * @param result 판정 결과. 판정 전이면 {@code null}
	 */
	public record MemberStatus(long userId, String login, @Nullable String avatarUrl, MembershipRole role,
			MemberDayProgress progress, boolean exemptionUsed, int solvedCount, @Nullable JudgmentResult result) {

	}

	/**
	 * 그날 센 풀이 하나.
	 *
	 * @param userId 푼 사용자
	 * @param login 푼 사용자의 GitHub 로그인 이름 (그날 멤버 목록에 없을 수도 있어서 함께 준다)
	 * @param platform 플랫폼 이름
	 * @param problemNo 문제 번호
	 * @param title 문제 제목. 모르면 {@code null}
	 * @param source 출처 이름 ({@code GITHUB}, {@code SELF_REPORT}, {@code RECOVERY})
	 * @param receivedAt 인정 시각
	 */
	public record RecentSolve(long userId, String login, String platform, String problemNo, @Nullable String title, String source,
			Instant receivedAt) {

	}

}
