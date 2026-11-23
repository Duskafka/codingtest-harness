package com.duskafka.studybot.github.persistence;

/** 웹훅을 보낸 곳 (glossary.md '웹훅 수신함'). P1은 GitHub만 받는다. 디스코드·카카오는 P3. */
public enum WebhookSource {

	GITHUB, DISCORD, KAKAO

}
