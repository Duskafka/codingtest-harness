package com.duskafka.studybot.auth.web;

import org.jspecify.annotations.Nullable;

/**
 * {@code GET /api/me} 응답.
 * @param id 사용자 ID
 * @param githubLogin GitHub 로그인 이름 (로그인할 때마다 갱신)
 * @param avatarUrl GitHub 프로필 사진 주소
 */
record MeResponse(long id, String githubLogin, @Nullable String avatarUrl) {
}
