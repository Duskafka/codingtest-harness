package com.duskafka.studybot.progress;

/**
 * 멤버 일의 판정 결과 (glossary.md {@code JudgmentResult}, R9). 미판정은 {@code null}이고, 기수 밖인 날은 판정하지 않아 결과가 없다
 * (OD-07). 판정은 S12가 한다. 배정(S09)은 결과가 있는지만 본다 (R4 '아직 판정 전').
 */
public enum JudgmentResult {

	SUCCESS, FAIL, EXEMPT_REST, EXEMPT_TICKET

}
