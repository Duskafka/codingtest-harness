package com.duskafka.studybot.github.webhook;

/**
 * GitHub 웹훅 이벤트 한 종류의 처리기. installation·installation_repositories는 S07, push는 S08이 구현한다.
 * 스프링 빈으로 두면 수신기가 이벤트 종류로 찾아 부른다. 이벤트 하나에 처리기는 하나다 (둘이면 앱이 뜨지 않는다).
 *
 * <p>지킬 것
 * <ul>
 * <li>새 트랜잭션 안에서 불린다. 처리기가 바꾼 DB 상태와 수신함 상태(PROCESSED·IGNORED)는 같은 트랜잭션으로 커밋된다.
 * 수신함 행은 그 전에 별도 트랜잭션으로 이미 커밋돼 있다 (5.3 P1).</li>
 * <li>정상으로 끝나면 {@link HandlingResult}를 돌려준다. 실패는 예외로 알린다. 그러면 처리기의 변경은 롤백되고 수신함은
 * {@code FAILED}가 되며, 예외 클래스와 메시지가 {@code last_error}에 남는다. 그래서 예외 메시지에 개인정보(이메일 등)를
 * 넣지 않는다. 응답은 실패해도 202다.</li>
 * <li>GitHub가 10초 안에 응답을 기다린다. GitHub API처럼 느린 외부 호출을 하지 않는다 [S33].</li>
 * <li>같은 전달은 한 번만 넘어온다 (수신함 UNIQUE). 다만 P2 워커는 실패한 행을 다시 넘길 수 있으니, 다시 불려도 결과가 같게
 * 만든다 (예: 유니크 제약으로 중복 무시).</li>
 * </ul>
 */
public interface GithubEventHandler {

	/** 맡는 이벤트. */
	GithubEvent event();

	/** 웹훅 하나를 처리한다. 위 '지킬 것'을 따른다. */
	HandlingResult handle(GithubWebhookEvent event);

}
