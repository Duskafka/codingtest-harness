package com.duskafka.studybot.notification.outbox;

import java.util.Objects;

/**
 * 웹 푸시로 보내는 내용 (암호화하기 전 평문 JSON). {@code notifications.payload}에 이 모양 그대로 저장하고 그대로 보낸다. 서비스
 * 워커(S16)가 {@code push} 이벤트에서 이것으로 알림을 띄우고, 누르면 {@code url}을 연다 (5.8 '만들 것').
 *
 * <p>작게(수 KB 안), 개인정보는 최소로 한다 (5.8 '주의할 것'). 사람 이름·GitHub 로그인명은 넣지 않는다. 스터디 이름은 사용자가 정한 이름이다.
 *
 * @param type 알림 종류 이름 (서비스 워커가 알림을 가르는 데 씀)
 * @param title 알림 제목 (스터디 이름)
 * @param body 알림 본문
 * @param url 누르면 열 화면 경로 (같은 출처의 상대 경로)
 */
public record PushMessage(String type, String title, String body, String url) {

	public PushMessage {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(body, "body");
		Objects.requireNonNull(url, "url");
	}

}
