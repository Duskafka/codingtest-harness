package com.duskafka.studybot.notification.discord;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 디스코드에 게시할 메시지 하나: 임베드 하나 (5.9 '디스코드 임베드 메시지'). 본문의 {@code allowed_mentions}는 늘 {@code {"parse": []}}다.
 * 스터디 이름 등에 {@code @everyone}·{@code @here}·멘션 문법이 들어 있어도 아무도 부르지 않는다 (security.md 9.7 '입력과 출력', S14 수용
 * 기준).
 *
 * @param title 임베드 제목 ({@value #MAX_TITLE}자까지 자름)
 * @param description 임베드 본문 ({@value #MAX_DESCRIPTION}자까지 자름)
 * @param url 제목을 누르면 열 주소. 없으면 {@code null}
 * @param color 임베드 왼쪽 줄 색 (0xRRGGBB)
 */
public record DiscordMessage(String title, String description, @Nullable String url, int color) {

	/** 디스코드 임베드 제목 한도. */
	static final int MAX_TITLE = 256;

	/** 디스코드 임베드 본문 한도. */
	static final int MAX_DESCRIPTION = 4096;

	public DiscordMessage {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(description, "description");
		title = cut(title, MAX_TITLE);
		description = cut(description, MAX_DESCRIPTION);
	}

	String toJson(JsonMapper jsonMapper) {
		ObjectNode root = jsonMapper.createObjectNode();
		root.putObject("allowed_mentions").putArray("parse");
		ObjectNode embed = root.putArray("embeds").addObject();
		embed.put("title", this.title);
		embed.put("description", this.description);
		if (this.url != null) {
			embed.put("url", this.url);
		}
		embed.put("color", this.color);
		return jsonMapper.writeValueAsString(root);
	}

	private static String cut(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max - 1) + "…";
	}

}
