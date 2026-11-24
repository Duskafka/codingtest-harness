package com.duskafka.studybot.common.async;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @Async}를 켠다. 실행기는 Spring Boot가 만드는 기본 실행기({@code applicationTaskExecutor})를 쓴다.
 *
 * <p>요청 스레드가 기다리지 않아도 되는 후속 작업에 쓴다. 첫 사용처는 S07의 로그인 뒤 설치 연결이다: 로그인 트랜잭션의 커밋 콜백은
 * 원래 DB 연결을 아직 쥔 채 불리므로, 거기서 새 트랜잭션을 열면 두 번째 연결을 기다리며 로그인 응답이 늦어질 수 있다
 * (PR #14 리뷰 반영, 사람이 정함 2026-11-23 "비동기로 실행"). 비동기 작업은 사라질 수 있으니, 쓰는 쪽은 DB 상태로 다시 맞출 길을
 * 따로 둔다 (architecture.md 4.2 '진실의 원천은 DB 상태').
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfiguration {

}
