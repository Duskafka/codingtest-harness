package com.duskafka.studybot.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 커밋 샘플 기반 파서 테스트 (S03). {@code src/test/resources/commit-samples/} 아래의 JSON 파일마다 테스트가 하나씩 돈다.
 * 샘플 형식은 그 폴더의 README.md에 있다. 사람이 샘플을 더하면 테스트가 는다.
 *
 * <p>샘플마다 출처 표시({@code provenance})가 있어야 한다. {@code 실제}는 사람이 준 실제 커밋, {@code 추측}은 공개 소스를 보고 만든 것이다.
 */
class CommitSampleTest {

	private static final String SAMPLE_ROOT = "commit-samples/";

	private static final List<String> PROVENANCES = List.of("실제", "추측");

	/** 필드 이름을 틀리면 조용히 무시되지 않게 모르는 필드에서 실패한다 (Jackson 3은 기본으로 무시한다). */
	private static final JsonMapper JSON = JsonMapper.builder()
		.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		.build();

	record ExpectedProblem(Platform platform, String problemNo, @Nullable String title) {
	}

	record CommitSample(String provenance, String origin, String description, String message, List<String> paths,
			List<ExpectedProblem> expected, @Nullable List<String> expectedUnidentified) {
	}

	static Stream<Arguments> samples() throws IOException {
		Resource[] resources = new PathMatchingResourcePatternResolver()
			.getResources("classpath*:" + SAMPLE_ROOT + "**/*.json");
		return Arrays.stream(resources).map(CommitSampleTest::load).sorted(Comparator.comparing(LoadedSample::name))
			.map(loaded -> arguments(Named.of(loaded.displayName(), loaded.sample())));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("samples")
	void sample(CommitSample sample) {
		assertThat(sample.provenance()).as("provenance는 '실제' 또는 '추측'").isIn(PROVENANCES);
		assertThat(sample.origin()).as("origin(출처)").isNotBlank();
		assertThat(sample.message()).as("message").isNotNull();
		assertThat(sample.paths()).as("paths").isNotNull();
		assertThat(sample.expected()).as("expected").isNotNull();

		ParsedCommit result = ProblemParser.parse(sample.message(), sample.paths());

		assertThat(result.refs()).as("식별한 문제")
			.containsExactlyElementsOf(sample.expected()
				.stream()
				.map(expected -> new ProblemRef(expected.platform(), expected.problemNo()))
				.toList());
		for (int i = 0; i < sample.expected().size(); i++) {
			String title = sample.expected().get(i).title();
			if (title != null) {
				assertThat(result.problems().get(i).title()).as("제목 (%d번째 문제)", i + 1).isEqualTo(title);
			}
		}
		assertThat(result.unidentifiedPaths()).as("확인 대기 후보")
			.containsExactlyElementsOf(Objects.requireNonNullElse(sample.expectedUnidentified(), List.of()));
	}

	private record LoadedSample(String name, String displayName, CommitSample sample) {
	}

	private static LoadedSample load(Resource resource) {
		String url = resource.getDescription();
		try (InputStream in = resource.getInputStream()) {
			String name = resourceName(resource);
			CommitSample sample = JSON.readValue(in, CommitSample.class);
			String displayName = "S03 샘플 [" + sample.provenance() + "] " + name + ": " + sample.description();
			return new LoadedSample(name, displayName, sample);
		}
		catch (IOException e) {
			throw new IllegalStateException("샘플을 읽지 못했어요: " + url, e);
		}
	}

	private static String resourceName(Resource resource) throws IOException {
		String url = resource.getURL().toString();
		return url.substring(url.lastIndexOf(SAMPLE_ROOT) + SAMPLE_ROOT.length());
	}

}
