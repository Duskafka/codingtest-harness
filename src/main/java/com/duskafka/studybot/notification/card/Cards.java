package com.duskafka.studybot.notification.card;

import com.duskafka.studybot.progress.JudgmentResult;
import com.duskafka.studybot.progress.MemberDayStatus;
import com.duskafka.studybot.progress.StudyDayBoards.BoardMember;
import com.duskafka.studybot.progress.StudyDayBoards.StudyDayBoard;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 공유 카드·디스코드 게시의 문구 (5.9 '카드 예시', S14 만들 것 3·5·6). 순수 계산이다.
 *
 * <ul>
 * <li>현황: 제목 {@code [스터디 이름] 11/20 인증 현황}, 요약 {@code 완료 5명 · 남음 2명 (민수, 지은) · 마감 24:00}. 면제가 있으면
 * {@code · 면제 N명}을 마감 앞에 더한다. 쉬는 날·판정하지 않는 날은 그 안내만 쓴다. 지금 탈퇴한 멤버(그날 다시 가입하지 않음)는 수와 이름에서
 * 뺀다. 공유 유도 웹 푸시의 'N명'(S13)과 맞추고, 탈퇴한 사람 이름이 '남음'으로 공유되지 않게 하려는 것이다 (사람이 정함 2026-11-28).</li>
 * <li>결과: 판정 대상 모두를 센다. 그날 탈퇴한 멤버도 그날까지 판정한다 (R14).</li>
 * <li>결과 문구: 제목 {@code [스터디 이름] 11/20 결과}, 요약 {@code 성공 5명 · 실패 2명 (민수, 지은) · 면제 1명}. 0명인 항목은 쓰지 않는다.</li>
 * <li>이름은 GitHub 로그인명이고 가입 순서로 앞 {@value #MAX_NAMES}명까지, 넘으면 {@code 외 N명}이다 (카카오톡 공유 카드는 짧아야 한다).
 * 벌금 금액은 넣지 않는다 (5.9, 사람이 정함 2026-11-28 'S14는 늘 뺌').</li>
 * <li>마감은 스터디 시간대의 24시간+ 표기다: 다음 날 00:00은 {@code 24:00}, 다음 날 02:00은 {@code 26:00} (S14 PR 판단 기록).</li>
 * </ul>
 */
public final class Cards {

	/** 요약에 쓰는 이름 수. */
	public static final int MAX_NAMES = 10;

	private static final String SEPARATOR = " · ";

	private Cards() {
	}

	/** 그날 현황 카드. */
	public static StatusCard status(String studyName, StudyDayBoard board, ZoneId zoneId, String url) {
		String title = "[" + studyName + "] " + dateText(board.studyDay()) + " 인증 현황";
		List<BoardMember> done = active(board.withStatus(MemberDayStatus.DONE));
		List<BoardMember> remaining = active(board.withStatus(MemberDayStatus.REMAINING));
		List<BoardMember> exempt = active(board.withStatus(MemberDayStatus.EXEMPT));
		String summary;
		if (!board.inSeason()) {
			summary = "오늘은 판정하지 않는 날이에요";
		}
		else if (board.restDay()) {
			summary = "오늘은 쉬는 날이에요";
		}
		else {
			StringBuilder text = new StringBuilder("완료 ").append(done.size()).append("명").append(SEPARATOR).append("남음 ")
				.append(remaining.size()).append("명");
			if (!remaining.isEmpty()) {
				text.append(" (").append(names(remaining)).append(")");
			}
			if (!exempt.isEmpty()) {
				text.append(SEPARATOR).append("면제 ").append(exempt.size()).append("명");
			}
			text.append(SEPARATOR).append("마감 ").append(deadlineText(board.studyDay(), board.endsAt(), zoneId));
			summary = text.toString();
		}
		return new StatusCard(title, summary, url, done.size(), remaining.size(), exempt.size());
	}

	/** 그날 판정 결과 카드 (이름 포함, 디스코드). */
	public static ResultCard result(String studyName, StudyDayBoard board, String url) {
		ResultCounts counts = resultCounts(board.resultCounts());
		String title = "[" + studyName + "] " + dateText(board.studyDay()) + " 결과";
		List<String> parts = new ArrayList<>();
		if (counts.success() > 0) {
			parts.add("성공 " + counts.success() + "명");
		}
		if (counts.fail() > 0) {
			parts.add("실패 " + counts.fail() + "명 (" + names(board.withResult(JudgmentResult.FAIL)) + ")");
		}
		if (counts.exempt() > 0) {
			parts.add("면제 " + counts.exempt() + "명");
		}
		return new ResultCard(title, parts.isEmpty() ? "판정한 멤버가 없어요" : String.join(SEPARATOR, parts), url);
	}

	/** 결과 알림 웹 푸시 본문 (이름 없음, 5.8 'payload는 개인정보 최소로'). 예: {@code 11/20 결과: 성공 1명 · 실패 2명}. */
	public static String resultPushBody(LocalDate studyDay, ResultCounts counts) {
		List<String> parts = new ArrayList<>();
		if (counts.success() > 0) {
			parts.add("성공 " + counts.success() + "명");
		}
		if (counts.fail() > 0) {
			parts.add("실패 " + counts.fail() + "명");
		}
		if (counts.exempt() > 0) {
			parts.add("면제 " + counts.exempt() + "명");
		}
		return dateText(studyDay) + " 결과: " + String.join(SEPARATOR, parts);
	}

	/**
	 * 그날 판정 결과 수. 면제는 면제권(EXEMPT_TICKET)만 센다. 쉬는 날은 모든 멤버가 EXEMPT_REST라(판정 규칙의 첫 갈래) 세지 않는다. 그래서
	 * 쉬는 날은 셋이 모두 0이다.
	 */
	public static ResultCounts resultCounts(Map<JudgmentResult, Integer> byResult) {
		return new ResultCounts(byResult.getOrDefault(JudgmentResult.SUCCESS, 0),
				byResult.getOrDefault(JudgmentResult.FAIL, 0), byResult.getOrDefault(JudgmentResult.EXEMPT_TICKET, 0));
	}

	/** 지금 탈퇴하지 않은 멤버만. 그날 탈퇴했다 다시 가입한 멤버는 들어간다. */
	private static List<BoardMember> active(List<BoardMember> members) {
		return members.stream().filter(BoardMember::active).toList();
	}

	/** 날짜 {@code 11/20}. */
	public static String dateText(LocalDate day) {
		return day.getMonthValue() + "/" + day.getDayOfMonth();
	}

	static String names(List<BoardMember> members) {
		List<String> logins = members.stream().limit(MAX_NAMES).map(BoardMember::login).toList();
		String text = String.join(", ", logins);
		return members.size() > MAX_NAMES ? text + " 외 " + (members.size() - MAX_NAMES) + "명" : text;
	}

	/** 마감 시각의 24시간+ 표기. 스터디 일 다음 날의 시각은 24를 더한다. */
	static String deadlineText(LocalDate studyDay, Instant endsAt, ZoneId zoneId) {
		LocalDateTime local = endsAt.atZone(zoneId).toLocalDateTime();
		long days = ChronoUnit.DAYS.between(studyDay, local.toLocalDate());
		long hours = days * 24 + local.getHour();
		return String.format("%02d:%02d", hours, local.getMinute());
	}

	/**
	 * 현황 카드.
	 *
	 * @param title 제목
	 * @param summary 요약 한 줄
	 * @param url 오늘 현황 화면 (절대 주소)
	 * @param done 완료 수
	 * @param remaining 남음 수
	 * @param exempt 면제 수
	 */
	public record StatusCard(String title, String summary, String url, int done, int remaining, int exempt) {

		public StatusCard {
			Objects.requireNonNull(title, "title");
			Objects.requireNonNull(summary, "summary");
			Objects.requireNonNull(url, "url");
		}

		/** '텍스트 복사'용 세 줄 (5.9 '공유가 안 되는 환경'). */
		public String text() {
			return this.title + "\n" + this.summary + "\n현황 보기: " + this.url;
		}

	}

	/**
	 * 결과 카드.
	 *
	 * @param title 제목
	 * @param summary 요약 한 줄 (실패한 멤버 이름 포함)
	 * @param url 그날 상세 화면 (절대 주소)
	 */
	public record ResultCard(String title, String summary, String url) {

		public ResultCard {
			Objects.requireNonNull(title, "title");
			Objects.requireNonNull(summary, "summary");
			Objects.requireNonNull(url, "url");
		}

	}

	/**
	 * 판정 결과 수.
	 *
	 * @param success 성공
	 * @param fail 실패
	 * @param exempt 면제권
	 */
	public record ResultCounts(int success, int fail, int exempt) {

		/** 결과(성공·실패·면제권)가 하나라도 있는지. 쉬는 날·판정 대상이 없는 날은 없어서 결과 알림을 만들지 않는다 (S14 PR 판단 기록). */
		public boolean hasDecisions() {
			return this.success > 0 || this.fail > 0 || this.exempt > 0;
		}

	}

}
