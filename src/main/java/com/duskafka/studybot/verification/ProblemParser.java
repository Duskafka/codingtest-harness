package com.duskafka.studybot.verification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * 커밋 메시지와 파일 경로로 문제를 식별한다 (docs/spec/processes/5.04-problem-identification.md). DB를 모르는 순수 함수다.
 *
 * <p>순서 (5.4 흐름도)
 * <ol>
 * <li>백준허브 일괄 업로드({@value #BULK_UPLOAD_MESSAGE}) 커밋은 예전에 푼 문제를 한꺼번에 올리는 것이라 결과 없음.</li>
 * <li>경로를 조각마다 정규화한다 ({@link PathSegments}).</li>
 * <li>풀이 파일이 아닌 파일(문서 {@code .md}, 백준허브 예제 입출력 {@code input*.txt}·{@code output*.txt}, 숨김 경로)은
 * 무시한다. 식별하지도, 확인 대기 후보로 돌려주지도 않는다. 이 판단은 정규화하기 전 이름으로 한다.</li>
 * <li>경로 규칙: 파일 바로 위 폴더가 문제 폴더다. 앞에 어떤 접두사가 있어도 된다.
 * <ul>
 * <li>백준: {@code …/백준/{티어}/{번호}. {제목}/파일}</li>
 * <li>프로그래머스: {@code …/프로그래머스/{레벨}/{번호}. {제목}/파일}</li>
 * <li>LeetCode: {@code …/{4자리 이상 번호}-{slug}/파일}. 파일 이름은 폴더 이름으로 시작하거나 {@code {slug}.확장자}</li>
 * </ul></li>
 * <li>메시지 규칙: 경로 규칙으로 하나도 찾지 못했고 메시지가 백준허브의 백준·프로그래머스 형식이면, 플랫폼과 제목은 메시지에서,
 * 번호는 파일 바로 위 폴더({@code {번호}} 또는 {@code {번호}. …})에서 보완한다. 번호가 정확히 하나일 때만 식별한다.</li>
 * <li>같은 문제의 파일 여러 개는 하나로 합친다. 남은 풀이 파일은 확인 대기 후보다 ({@link ParsedCommit#unidentifiedPaths()}).</li>
 * </ol>
 *
 * <p>형식은 백준허브(manifest 1.4.29)·LeetHub 공개 소스를 보고 정했다. 실제 커밋 샘플(H-01)로 확인하지 못했다.
 */
public final class ProblemParser {

	/** 파서 버전. 식별 규칙을 바꾸면 올린다. 풀이 기록에 남긴다 (5.4, S08). */
	public static final int VERSION = 1;

	/** 백준허브 일괄 업로드 커밋의 메시지. */
	static final String BULK_UPLOAD_MESSAGE = "전체 코드 업로드 -BaekjoonHub";

	private static final String BOJ_FOLDER = "백준";

	private static final String PROGRAMMERS_FOLDER = "프로그래머스";

	/** 백준허브의 백준 등급 폴더: 티어 이름. 경로 템플릿의 {@code ${levelFull}}을 쓰면 세부 등급이 붙는다. */
	private static final Pattern BOJ_TIER = Pattern
		.compile("(?:Bronze|Silver|Gold|Platinum|Diamond|Ruby)(?: (?:I|II|III|IV|V))?|Unrated|Master");

	/** 백준허브의 프로그래머스 레벨 폴더. */
	private static final Pattern PROGRAMMERS_LEVEL = Pattern.compile("[0-9]|lv[0-9]|Lv\\.[0-9]|unrated");

	/** 백준허브의 문제 폴더: {@code {번호}. {제목}}. */
	private static final Pattern PROBLEM_FOLDER = Pattern.compile("([0-9]+)\\.\\s*(.*)");

	/** LeetHub의 문제 폴더: 앞에 0을 채운 4자리 이상 번호와 slug. */
	private static final Pattern LEETCODE_FOLDER = Pattern.compile("([0-9]{4,})-([a-z0-9]+(?:-[a-z0-9]+)*)");

	/** 메시지 규칙에서 번호를 보완하는 폴더: {@code {번호}} 또는 {@code {번호}. …}. */
	private static final Pattern NUMBER_FOLDER = Pattern.compile("([0-9]+)(?:\\..*)?");

	private static final Pattern BOJ_MESSAGE = Pattern.compile(
			"\\[(?:(?:Bronze|Silver|Gold|Platinum|Diamond|Ruby) (?:I|II|III|IV|V)|Unrated|Master)] Title: (.+?), Time: .*-BaekjoonHub");

	private static final Pattern PROGRAMMERS_MESSAGE = Pattern
		.compile("\\[(?:level [0-9]+|lv[0-9]+)] Title: (.+?), Time: .*-BaekjoonHub");

	/** 백준허브가 예제 입출력 저장 옵션으로 올리는 파일. */
	private static final Pattern SAMPLE_IO_FILE = Pattern.compile("(?:input|output)[0-9]*\\.txt",
			Pattern.CASE_INSENSITIVE);

	private ProblemParser() {
	}

	/**
	 * 커밋 하나를 분석한다.
	 *
	 * @param commitMessage 커밋 메시지 전체. 첫 줄만 본다
	 * @param paths 추가·수정된 파일 경로
	 * @return 식별한 문제와 확인 대기 후보
	 */
	public static ParsedCommit parse(String commitMessage, List<String> paths) {
		Objects.requireNonNull(commitMessage, "commitMessage");
		Objects.requireNonNull(paths, "paths");
		List<PathSegments> candidates = paths.stream()
			.map(PathSegments::of)
			.filter(ProblemParser::isSolutionCandidate)
			.toList();
		String subject = commitMessage.lines().findFirst().orElse("").strip();
		if (subject.equals(BULK_UPLOAD_MESSAGE)) {
			return ParsedCommit.empty();
		}

		Map<ProblemRef, IdentifiedProblem> identified = new LinkedHashMap<>();
		Set<String> unidentified = new LinkedHashSet<>();
		for (PathSegments path : candidates) {
			Optional<IdentifiedProblem> problem = byPath(path);
			if (problem.isPresent()) {
				identified.putIfAbsent(problem.get().ref(), problem.get());
			}
			else {
				unidentified.add(path.raw());
			}
		}
		if (identified.isEmpty()) {
			byMessage(subject, candidates).ifPresent(match -> {
				identified.put(match.problem().ref(), match.problem());
				unidentified.removeAll(match.consumedPaths());
			});
		}
		return new ParsedCommit(List.copyOf(identified.values()), List.copyOf(unidentified));
	}

	/** 풀이 파일 후보인지. 전각을 접기 전 이름으로 본다 ({@link PathSegments} 참고). */
	private static boolean isSolutionCandidate(PathSegments path) {
		if (path.rawSegments().stream().anyMatch(segment -> segment.startsWith("."))) {
			return false;
		}
		String fileName = path.rawFileName();
		return !fileName.toLowerCase(Locale.ROOT).endsWith(".md") && !SAMPLE_IO_FILE.matcher(fileName).matches();
	}

	private static Optional<IdentifiedProblem> byPath(PathSegments path) {
		return baekjoonHub(path).or(() -> leetHub(path));
	}

	private static Optional<IdentifiedProblem> baekjoonHub(PathSegments path) {
		String folder = path.fromEnd(1);
		String level = path.fromEnd(2);
		String platformFolder = path.fromEnd(3);
		if (folder == null || level == null || platformFolder == null) {
			return Optional.empty();
		}
		Platform platform;
		if (platformFolder.equals(BOJ_FOLDER) && BOJ_TIER.matcher(level).matches()) {
			platform = Platform.BOJ;
		}
		else if (platformFolder.equals(PROGRAMMERS_FOLDER) && PROGRAMMERS_LEVEL.matcher(level).matches()) {
			platform = Platform.PROGRAMMERS;
		}
		else {
			return Optional.empty();
		}
		Matcher problemFolder = PROBLEM_FOLDER.matcher(folder);
		if (!problemFolder.matches()) {
			return Optional.empty();
		}
		return canonicalNumber(problemFolder.group(1)).map(number -> new IdentifiedProblem(
				new ProblemRef(platform, number), blankToNull(problemFolder.group(2)), path.raw()));
	}

	private static Optional<IdentifiedProblem> leetHub(PathSegments path) {
		String folder = path.fromEnd(1);
		if (folder == null) {
			return Optional.empty();
		}
		Matcher problemFolder = LEETCODE_FOLDER.matcher(folder);
		if (!problemFolder.matches()) {
			return Optional.empty();
		}
		String slug = problemFolder.group(2);
		String stem = fileStem(path.fileName());
		// 번호-영문 모양의 폴더(예: 2024-winter)를 LeetCode로 잘못 확정하지 않도록 LeetHub의 파일 이름까지 본다.
		// LeetHub는 폴더 이름으로 시작하는 파일 이름을 쓰고(접미사·타임스탬프 옵션 포함), 5.4 표의 예는 slug.확장자다.
		if (slug.chars().noneMatch(c -> c >= 'a' && c <= 'z') || !(stem.startsWith(folder) || stem.equals(slug))) {
			return Optional.empty();
		}
		// slug는 제목이 아니라서 제목은 비워 둔다.
		return canonicalNumber(problemFolder.group(1))
			.map(number -> new IdentifiedProblem(new ProblemRef(Platform.LEETCODE, number), null, path.raw()));
	}

	private static Optional<MessageMatch> byMessage(String subject, List<PathSegments> candidates) {
		Platform platform;
		Matcher message = BOJ_MESSAGE.matcher(subject);
		if (message.matches()) {
			platform = Platform.BOJ;
		}
		else {
			message = PROGRAMMERS_MESSAGE.matcher(subject);
			if (!message.matches()) {
				return Optional.empty();
			}
			platform = Platform.PROGRAMMERS;
		}
		String title = blankToNull(message.group(1));

		Map<String, List<String>> pathsByNumber = new LinkedHashMap<>();
		for (PathSegments path : candidates) {
			numberFolder(path).ifPresent(
					number -> pathsByNumber.computeIfAbsent(number, key -> new ArrayList<>()).add(path.raw()));
		}
		if (pathsByNumber.size() != 1) {
			return Optional.empty();
		}
		Map.Entry<String, List<String>> only = pathsByNumber.entrySet().iterator().next();
		IdentifiedProblem problem = new IdentifiedProblem(new ProblemRef(platform, only.getKey()), title,
				only.getValue().getFirst());
		return Optional.of(new MessageMatch(problem, only.getValue()));
	}

	private static Optional<String> numberFolder(PathSegments path) {
		String folder = path.fromEnd(1);
		if (folder == null) {
			return Optional.empty();
		}
		Matcher matcher = NUMBER_FOLDER.matcher(folder);
		return matcher.matches() ? canonicalNumber(matcher.group(1)) : Optional.empty();
	}

	/** 마지막 확장자를 뗀 파일 이름. */
	private static String fileStem(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(0, dot) : fileName;
	}

	/** 앞자리 0을 뗀다. 0뿐이면 문제 번호가 아니다. */
	private static Optional<String> canonicalNumber(String digits) {
		String stripped = digits.replaceFirst("^0+", "");
		return stripped.isEmpty() ? Optional.empty() : Optional.of(stripped);
	}

	private static @Nullable String blankToNull(String value) {
		String stripped = value.strip();
		return stripped.isEmpty() ? null : stripped;
	}

	private record MessageMatch(IdentifiedProblem problem, List<String> consumedPaths) {
	}

}
