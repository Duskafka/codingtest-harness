package com.duskafka.studybot.github.api;

/**
 * 설치에서 볼 수 있는 레포 하나 ({@code GET /installation/repositories}의 항목에서 쓰는 값만).
 *
 * @param id 레포 ID. 이름이 바뀌어도 그대로다
 * @param fullName {@code owner/name}
 * @param defaultBranch 기본 브랜치
 * @param isPrivate 비공개 레포인지
 */
public record InstalledRepository(long id, String fullName, String defaultBranch, boolean isPrivate) {
}
