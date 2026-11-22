package com.duskafka.studybot.verification;

import static com.duskafka.studybot.verification.Platform.BOJ;
import static com.duskafka.studybot.verification.Platform.LEETCODE;
import static com.duskafka.studybot.verification.Platform.PROGRAMMERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 문제 식별 파서 (S03, 5.4). 경로·메시지 형식은 백준허브·LeetHub 공개 소스를 보고 만든 것이라 실제 커밋으로 확인하지 못했다
 * (사람 할 일 H-01). 실제 샘플 기반 테스트는 {@link CommitSampleTest}다.
 *
 * <p>백준허브는 제목의 공백을 U+2005(FOUR-PER-EM SPACE)로, ASCII 기호를 전각 문자로 바꿔 경로에 쓴다.
 */
class ProblemParserTest {

	/** 백준허브가 경로의 제목에 쓰는 공백. */
	private static final String SP = " ";

	private static final String MAZE = "미로" + SP + "탐색";

	private static final String BOJ_MAZE_MESSAGE = "[Silver I] Title: 미로 탐색, Time: 76 ms, Memory: 14532 KB -BaekjoonHub";

	private static final String BOJ_MAZE_DIR = "백준/Silver/2178. " + MAZE;

	private static final String BOJ_MAZE_SOURCE = BOJ_MAZE_DIR + "/" + MAZE + ".java";

	private static final String PG_TITLE = "문자열" + SP + "내" + SP + "p와" + SP + "y의" + SP + "개수";

	private static final String PG_MESSAGE = "[level 1] Title: 문자열 내 p와 y의 개수, Time: 0.03 ms, Memory: 77.8 MB -BaekjoonHub";

	private static final String PG_SOURCE = "프로그래머스/1/12916. " + PG_TITLE + "/" + PG_TITLE + ".java";

	private static final String LEETHUB_MESSAGE = "Time: 0 ms (100.00%), Space: 44.9 MB (60.12%) - LeetHub";

	private static ParsedCommit parse(String message, String... paths) {
		return ProblemParser.parse(message, Arrays.asList(paths));
	}

	private static ProblemRef ref(Platform platform, String problemNo) {
		return new ProblemRef(platform, problemNo);
	}

	// 수용 기준

	@Test
	@DisplayName("S03: 백준허브의 백준 형식 커밋 → (백준, 번호)")
	void baekjoonHubBojCommit() {
		ParsedCommit result = parse(BOJ_MAZE_MESSAGE, BOJ_MAZE_SOURCE, BOJ_MAZE_DIR + "/README.md");

		assertThat(result.problems()).containsExactly(new IdentifiedProblem(ref(BOJ, "2178"), "미로 탐색", BOJ_MAZE_SOURCE));
		assertThat(result.unidentifiedPaths()).isEmpty();
	}

	@Test
	@DisplayName("S03: 백준허브의 프로그래머스 형식 커밋 → (프로그래머스, 번호)")
	void baekjoonHubProgrammersCommit() {
		String readme = "프로그래머스/1/12916. " + PG_TITLE + "/README.md";

		ParsedCommit result = parse(PG_MESSAGE, PG_SOURCE, readme);

		assertThat(result.problems())
			.containsExactly(new IdentifiedProblem(ref(PROGRAMMERS, "12916"), "문자열 내 p와 y의 개수", PG_SOURCE));
		assertThat(result.unidentifiedPaths()).isEmpty();
	}

	@Test
	@DisplayName("S03: 경로에 전각 문자(＋, ［ ］)가 있어도 번호로 식별한다")
	void fullWidthCharactersInPath() {
		ParsedCommit plus = parse("[Bronze V] Title: A+B, Time: 68 ms, Memory: 14164 KB -BaekjoonHub",
				"백준/Bronze/1000. A＋B/A＋B.java");
		ParsedCommit brackets = parse("커밋", "백준/Gold/1234. ［연습］" + SP + "A＋B/［연습］" + SP + "A＋B.cpp");

		assertThat(plus.refs()).containsExactly(ref(BOJ, "1000"));
		assertThat(brackets.refs()).containsExactly(ref(BOJ, "1234"));
	}

	@Test
	@DisplayName("S03: 제목은 비교에 쓰지 않는다. 번호가 같으면 제목이 달라도 같은 문제다")
	void titleIsNotPartOfIdentity() {
		ParsedCommit first = parse("커밋", "백준/Bronze/1000. A＋B/A＋B.py");
		ParsedCommit second = parse("커밋", "백준/Bronze/1000. 바뀐" + SP + "제목/풀이.py");

		assertThat(first.refs()).isEqualTo(second.refs()).containsExactly(ref(BOJ, "1000"));
		assertThat(first.problems().getFirst().title()).isNotEqualTo(second.problems().getFirst().title());
	}

	@Test
	@DisplayName("S03: NFD로 온 한글 경로도 식별한다")
	void nfdKoreanPath() {
		String nfdBoj = Normalizer.normalize(BOJ_MAZE_SOURCE, Normalizer.Form.NFD);
		String nfdProgrammers = Normalizer.normalize(PG_SOURCE, Normalizer.Form.NFD);
		assertThat(nfdBoj).as("테스트 입력이 실제로 NFD인지").isNotEqualTo(BOJ_MAZE_SOURCE);

		ParsedCommit result = parse("커밋", nfdBoj, nfdProgrammers);

		assertThat(result.refs()).containsExactly(ref(BOJ, "2178"), ref(PROGRAMMERS, "12916"));
		assertThat(result.problems().getFirst().title()).as("제목은 NFC로").isEqualTo("미로 탐색");
		assertThat(result.problems().getFirst().rawPath()).as("원본 경로는 그대로").isEqualTo(nfdBoj);
	}

	@Test
	@DisplayName("S03: 한 커밋에 같은 문제의 파일이 여러 개면 결과는 하나다")
	void sameProblemFilesMergeIntoOne() {
		ParsedCommit result = parse(BOJ_MAZE_MESSAGE, BOJ_MAZE_SOURCE, BOJ_MAZE_DIR + "/README.md",
				BOJ_MAZE_DIR + "/input1.txt", BOJ_MAZE_DIR + "/output1.txt", BOJ_MAZE_DIR + "/" + MAZE + "_002.java",
				BOJ_MAZE_DIR + "/" + MAZE + ".py");

		assertThat(result.problems()).containsExactly(new IdentifiedProblem(ref(BOJ, "2178"), "미로 탐색", BOJ_MAZE_SOURCE));
		assertThat(result.unidentifiedPaths()).isEmpty();
	}

	@Test
	@DisplayName("S03: README만 고친 커밋 → 결과 없음")
	void readmeOnlyCommit() {
		assertThat(parse("Update README.md", "README.md")).isEqualTo(ParsedCommit.empty());
		assertThat(parse("README 정리", BOJ_MAZE_DIR + "/README.md", "백준/README.md")).isEqualTo(ParsedCommit.empty());
	}

	@Test
	@DisplayName("S03: 백준 1000번과 프로그래머스 1000번은 다른 ProblemRef다")
	void sameNumberOnDifferentPlatformsIsDifferent() {
		ParsedCommit result = parse("두 문제", "백준/Bronze/1000. A＋B/A＋B.java", "프로그래머스/0/1000. 예시/예시.java");

		assertThat(result.refs()).containsExactly(ref(BOJ, "1000"), ref(PROGRAMMERS, "1000"));
		assertThat(result.refs().get(0)).isNotEqualTo(result.refs().get(1));
	}

	@Test
	@DisplayName("S03 [추측]: LeetHub 형식 커밋 → (LeetCode, 번호). 샘플이 없어 공개 소스로 만든 형식")
	void leetHubCommit() {
		assertThat(parse(LEETHUB_MESSAGE, "0001-two-sum/0001-two-sum.java").refs()).containsExactly(ref(LEETCODE, "1"));
		assertThat(parse(LEETHUB_MESSAGE, "0001-two-sum/two-sum.java").refs()).containsExactly(ref(LEETCODE, "1"));
		assertThat(parse(LEETHUB_MESSAGE, "LeetCode/Easy/0001-two-sum/0001-two-sum.py").refs())
			.containsExactly(ref(LEETCODE, "1"));
		assertThat(parse(LEETHUB_MESSAGE, "java/0015-3sum/0015-3sum.java").refs()).containsExactly(ref(LEETCODE, "15"));
	}

	@Test
	@DisplayName("S03 [추측]: LeetHub의 README·노트 커밋 → 결과 없음")
	void leetHubReadmeCommit() {
		assertThat(parse("Create readme : 0001-two-sum", "0001-two-sum/README.md")).isEqualTo(ParsedCommit.empty());
		assertThat(parse("Attach NOTES - LeetHub", "0001-two-sum/NOTES.md")).isEqualTo(ParsedCommit.empty());
	}

	// 백준허브의 특별한 커밋

	@Test
	@DisplayName("S03: 백준허브 일괄 업로드(전체 코드 업로드) 커밋 → 결과 없음, 확인 대기 후보도 없음 (사람 결정)")
	void baekjoonHubBulkUploadIsIgnored() {
		ParsedCommit result = parse("전체 코드 업로드 -BaekjoonHub", BOJ_MAZE_SOURCE, BOJ_MAZE_DIR + "/README.md",
				"백준/Bronze/1000. A＋B/A＋B.java", "백준/Bronze/1000. A＋B/README.md", "프로그래머스/1/x/x.java");

		assertThat(result).isEqualTo(ParsedCommit.empty());
	}

	@Test
	@DisplayName("S03: 백준허브 예제 입출력만 올린 커밋 → 결과 없음")
	void baekjoonHubExamplesOnlyIsIgnored() {
		ParsedCommit result = parse("[Silver I] Title: 미로 탐색 - 예제 입출력 -BaekjoonHub", BOJ_MAZE_DIR + "/input1.txt",
				BOJ_MAZE_DIR + "/output1.txt", BOJ_MAZE_DIR + "/input2.txt", BOJ_MAZE_DIR + "/output2.txt");

		assertThat(result).isEqualTo(ParsedCommit.empty());
	}

	// 경로 규칙

	@Test
	@DisplayName("S03: 플랫폼 폴더 앞의 접두사(언어별 폴더 등)는 상관없다")
	void prefixBeforePlatformFolder() {
		assertThat(parse(BOJ_MAZE_MESSAGE, "Java/" + BOJ_MAZE_SOURCE).refs()).containsExactly(ref(BOJ, "2178"));
		assertThat(parse("커밋", "algorithm/coding-test/" + PG_SOURCE).refs()).containsExactly(ref(PROGRAMMERS, "12916"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "Unrated", "Bronze", "Silver", "Gold", "Platinum", "Diamond", "Ruby", "Master", "Gold V",
			"Ruby I", "Silver III" })
	@DisplayName("S03: 백준 등급 폴더는 티어 이름(세부 등급을 붙여도 됨)")
	void bojTierFolders(String tier) {
		assertThat(parse("커밋", "백준/" + tier + "/1234. 제목/제목.java").refs()).containsExactly(ref(BOJ, "1234"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "1", "5", "lv2", "Lv.3", "unrated" })
	@DisplayName("S03: 프로그래머스 레벨 폴더는 숫자, lvN, Lv.N, unrated")
	void programmersLevelFolders(String level) {
		assertThat(parse("커밋", "프로그래머스/" + level + "/12916. 제목/제목.java").refs())
			.containsExactly(ref(PROGRAMMERS, "12916"));
	}

	@Test
	@DisplayName("S03: 등급 폴더가 티어가 아니거나 문제 폴더 바로 아래 파일이 아니면 경로 규칙에 맞지 않는다")
	void pathRuleIsStrict() {
		assertThat(parse("커밋", "백준/Easy/1234. 제목/제목.java").refs()).isEmpty();
		assertThat(parse("커밋", "백준/1234. 제목/제목.java").refs()).isEmpty();
		assertThat(parse("커밋", "백준/Gold/1234. 제목/src/Main.java").refs()).isEmpty();
		assertThat(parse("커밋", "프로그래머스/고득점 Kit/12916. 제목/제목.java").refs()).isEmpty();
	}

	@Test
	@DisplayName("S03: 문제 폴더의 번호 뒤 공백은 없어도 된다")
	void problemFolderWithoutSpaceAfterDot() {
		assertThat(parse("커밋", "백준/Gold/1234.제목/제목.java").refs()).containsExactly(ref(BOJ, "1234"));
	}

	@Test
	@DisplayName("S03: 제목의 전각 빗금(／)은 경로 구분자가 되지 않는다")
	void fullWidthSlashDoesNotSplitPath() {
		ParsedCommit result = parse("커밋", "백준/Silver/1234. A／B/A／B.java");

		assertThat(result.problems()).containsExactly(
				new IdentifiedProblem(ref(BOJ, "1234"), "A/B", "백준/Silver/1234. A／B/A／B.java"));
	}

	@Test
	@DisplayName("S03 [추측]: LeetCode 폴더는 4자리 이상 번호-slug이고, slug에 영문자가 있어야 한다")
	void leetCodeFolderRule() {
		assertThat(parse("커밋", "0042-trapping-rain-water/0042-trapping-rain-water.py").refs())
			.containsExactly(ref(LEETCODE, "42"));
		assertThat(parse("커밋", "0650-2-keys-keyboard/0650-2-keys-keyboard.py").refs())
			.containsExactly(ref(LEETCODE, "650"));
		assertThat(parse("커밋", "10001-future-problem/10001-future-problem.py").refs())
			.containsExactly(ref(LEETCODE, "10001"));
		assertThat(parse("커밋", "2026-11-22/Main.java").refs()).as("날짜 폴더").isEmpty();
		assertThat(parse("커밋", "42-trapping-rain-water/a.py").refs()).as("4자리 미만").isEmpty();
		assertThat(parse("커밋", "1000-A＋B/A＋B.java").refs()).as("slug가 아님").isEmpty();
	}

	@Test
	@DisplayName("S03: LeetCode 문제의 제목은 경로로 알 수 없어 비워 둔다")
	void leetCodeHasNoTitle() {
		assertThat(parse(LEETHUB_MESSAGE, "0001-two-sum/0001-two-sum.java").problems().getFirst().title()).isNull();
	}

	// 메시지 규칙

	@Test
	@DisplayName("S03: 경로 규칙에 맞지 않으면 백준허브 메시지로 플랫폼·제목을, 바로 위 폴더의 번호로 문제를 식별한다")
	void messageRuleWithNumberFromPath() {
		ParsedCommit boj = parse(BOJ_MAZE_MESSAGE, "백준/2178/" + MAZE + ".java");
		ParsedCommit bojScore = parse("[Gold III] Title: 서브태스크, Time: 4 ms, Memory: 2020 KB, Score: 37 point -BaekjoonHub",
				"algo/12345. 서브태스크/서브태스크.cc");
		ParsedCommit programmers = parse(PG_MESSAGE, "programmers/12916/solution.java");

		assertThat(boj.problems())
			.containsExactly(new IdentifiedProblem(ref(BOJ, "2178"), "미로 탐색", "백준/2178/" + MAZE + ".java"));
		assertThat(bojScore.refs()).containsExactly(ref(BOJ, "12345"));
		assertThat(programmers.refs()).containsExactly(ref(PROGRAMMERS, "12916"));
		assertThat(programmers.unidentifiedPaths()).isEmpty();
	}

	@Test
	@DisplayName("S03: 메시지 규칙으로 보완할 번호가 여러 개거나 없으면 식별하지 않는다")
	void messageRuleNeedsExactlyOneNumber() {
		ParsedCommit ambiguous = parse(BOJ_MAZE_MESSAGE, "algo/1000/a.java", "algo/2000/b.java");
		ParsedCommit missing = parse(BOJ_MAZE_MESSAGE, "algo/미로 탐색/a.java");

		assertThat(ambiguous.refs()).isEmpty();
		assertThat(ambiguous.unidentifiedPaths()).containsExactly("algo/1000/a.java", "algo/2000/b.java");
		assertThat(missing.refs()).isEmpty();
		assertThat(missing.unidentifiedPaths()).containsExactly("algo/미로 탐색/a.java");
	}

	@Test
	@DisplayName("S03: 백준허브 메시지가 아니면 번호 폴더만으로 식별하지 않는다")
	void numberFolderAloneIsNotEnough() {
		ParsedCommit result = parse("2178 풀이", "백준/2178/Main.java");

		assertThat(result.refs()).isEmpty();
		assertThat(result.unidentifiedPaths()).containsExactly("백준/2178/Main.java");
	}

	@Test
	@DisplayName("S03: 경로 규칙이 메시지 규칙보다 먼저다")
	void pathRuleWinsOverMessage() {
		assertThat(parse(BOJ_MAZE_MESSAGE, PG_SOURCE).refs()).containsExactly(ref(PROGRAMMERS, "12916"));
	}

	@Test
	@DisplayName("S03: 메시지는 첫 줄만 본다")
	void onlyFirstLineOfMessage() {
		assertThat(parse(BOJ_MAZE_MESSAGE + "\n\n본문", "백준/2178/Main.java").refs()).containsExactly(ref(BOJ, "2178"));
		assertThat(parse("정리\n\n전체 코드 업로드 -BaekjoonHub", BOJ_MAZE_SOURCE).refs()).containsExactly(ref(BOJ, "2178"));
	}

	// 확인 대기 후보

	@Test
	@DisplayName("S03: 식별하지 못한 풀이 파일만 확인 대기 후보로 돌려준다. 문서·예제·숨김 파일은 무시한다")
	void unidentifiedCandidatesExcludeNonSolutionFiles() {
		ParsedCommit result = parse("오늘 풀이", "algorithms/day3/Main.java", "algorithms/README.md", "notes/풀이.MD",
				".github/workflows/ci.yml", "algorithms/.idea/misc.xml", "algorithms/day3/input.txt",
				"algorithms/day3/output12.txt", "algorithms/day3/data.txt", "algorithms/day3/Main.java");

		assertThat(result.problems()).isEmpty();
		assertThat(result.unidentifiedPaths()).containsExactly("algorithms/day3/Main.java", "algorithms/day3/data.txt");
	}

	@Test
	@DisplayName("S03: 식별한 파일과 식별하지 못한 파일이 섞이면 둘 다 돌려준다")
	void mixedCommit() {
		ParsedCommit result = parse("풀이", BOJ_MAZE_SOURCE, "scratch/Main.java");

		assertThat(result.refs()).containsExactly(ref(BOJ, "2178"));
		assertThat(result.unidentifiedPaths()).containsExactly("scratch/Main.java");
	}

	@Test
	@DisplayName("S03: 결과는 경로 목록에서 처음 나온 순서다")
	void resultOrderFollowsPaths() {
		ParsedCommit result = parse("두 문제", "백준/Silver/2000. 둘/둘.java", "백준/Bronze/1000. A＋B/A＋B.java",
				"백준/Silver/2000. 둘/README.md");

		assertThat(result.refs()).containsExactly(ref(BOJ, "2000"), ref(BOJ, "1000"));
	}

	@Test
	@DisplayName("S03: 빈 메시지와 빈 경로 목록 → 결과 없음")
	void emptyInput() {
		assertThat(ProblemParser.parse("", List.of())).isEqualTo(ParsedCommit.empty());
	}

	@Test
	@DisplayName("S03: 메시지·경로 목록·경로는 null일 수 없다")
	void nullArguments() {
		assertThatNullPointerException().isThrownBy(() -> ProblemParser.parse(null, List.of()));
		assertThatNullPointerException().isThrownBy(() -> ProblemParser.parse("커밋", null));
		assertThatNullPointerException().isThrownBy(() -> ProblemParser.parse("커밋", Arrays.asList("a.java", null)));
	}

}
