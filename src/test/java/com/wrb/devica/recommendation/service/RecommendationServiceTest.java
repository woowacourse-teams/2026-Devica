package com.wrb.devica.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wrb.devica.common.exception.BusinessErrorCode;
import com.wrb.devica.common.exception.BusinessException;
import com.wrb.devica.product.domain.SpecValue;
import com.wrb.devica.purpose.domain.UsagePurposeCode;
import com.wrb.devica.recommendation.domain.InputType;
import com.wrb.devica.recommendation.domain.Option;
import com.wrb.devica.recommendation.domain.Question;
import com.wrb.devica.recommendation.domain.RecommendedSpec;
import com.wrb.devica.recommendation.domain.Step;
import com.wrb.devica.recommendation.domain.Survey;
import com.wrb.devica.recommendation.domain.SurveyResponse;
import com.wrb.devica.recommendation.dto.RecommendationResponse;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
class RecommendationServiceTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String SNAPSHOT = "/recommendation/backend-development-snapshot.json";
    private static final long SNAPSHOT_SEED = 242L;
    private static final int SNAPSHOT_ATTEMPTS = 20_000;
    private static final int CASES_PER_SPECS = 3;
    private static final String FEEDBACK_CASE_PREFIX = "피드백 #";

    /**
     * 규칙을 고칠 때 먼저 확인할 대표 사용자다. 스냅숏 맨 앞에 이름과 함께 고정된다.
     */
    private static final Map<String, Map<String, List<String>>> PERSONAS = personas();

    @Autowired
    private RecommendationService recommendationService;

    private static Map<String, Map<String, List<String>>> personas() {
        Map<String, Map<String, List<String>>> personas = new LinkedHashMap<>();
        personas.put("아직 아무것도 정하지 않은 입문자", answers(
            "PREFERRED_OS", "UNDECIDED", "PROGRAMMING_LANGUAGE", "UNDECIDED", "IDE", "UNKNOWN",
            "AI_CODING_TOOL", "NONE", "DEV_ENVIRONMENT_SETUP", "NO_EXPERIENCE", "SLOWDOWN", "UNKNOWN",
            "STORAGE_SHORTAGE", "NEVER", "OVERHEATING", "UNKNOWN", "USAGE_PERIOD", "UNDECIDED"));
        personas.put("M 칩 Mac 에서 빌드가 답답한 Java 개발자", answers(
            "CURRENT_OS", "MACOS", "CURRENT_MAC_CPU", "BASIC", "CURRENT_MEMORY", "GB_16",
            "CURRENT_STORAGE", "UNDER_1TB", "PREFERRED_OS", "MACOS", "PROGRAMMING_LANGUAGE", "JAVA_FAMILY",
            "IDE", "JETBRAINS", "AI_CODING_TOOL", "IDE_EXTENSION", "DEV_ENVIRONMENT_SETUP", "DOCKER_FEW",
            "SLOWDOWN", "SOMETIMES", "STORAGE_SHORTAGE", "ONCE_OR_TWICE", "BUILD_WAIT", "OFTEN",
            "OVERHEATING", "FINE", "USAGE_PERIOD", "THREE_TO_FOUR_YEARS"));
        personas.put("M Pro Mac 을 쓰다 Windows 로 옮기려는 Java 개발자", answers(
            "CURRENT_OS", "MACOS", "CURRENT_MAC_CPU", "PRO", "PREFERRED_OS", "WINDOWS",
            "PROGRAMMING_LANGUAGE", "JAVA_FAMILY", "IDE", "JETBRAINS", "AI_CODING_TOOL", "NONE",
            "DEV_ENVIRONMENT_SETUP", "DOCKER_MANY", "SLOWDOWN", "SOMETIMES", "STORAGE_SHORTAGE", "ONCE_OR_TWICE",
            "BUILD_WAIT", "OFTEN", "OVERHEATING", "OFTEN", "USAGE_PERIOD", "THREE_TO_FOUR_YEARS"));
        personas.put("모든 부담이 겹치고 오래 쓰려는 사용자", answers(
            "PREFERRED_OS", "MACOS", "PROGRAMMING_LANGUAGE", List.of("JAVA_FAMILY", "NODE_TYPESCRIPT"),
            "IDE", "MULTIPLE", "AI_CODING_TOOL", "AI_EDITOR", "DEV_ENVIRONMENT_SETUP", "DOCKER_MANY",
            "SLOWDOWN", "OFTEN", "STORAGE_SHORTAGE", "OFTEN", "BUILD_WAIT", "OFTEN", "OVERHEATING", "OFTEN",
            "USAGE_PERIOD", "FIVE_PLUS_YEARS"));
        personas.put("원격 서버에서 개발하고 2년만 쓸 사용자", answers(
            "PREFERRED_OS", "WINDOWS", "PROGRAMMING_LANGUAGE", "OTHERS", "IDE", "LIGHT_EDITOR",
            "AI_CODING_TOOL", "NONE", "DEV_ENVIRONMENT_SETUP", "REMOTE", "SLOWDOWN", "FINE",
            "STORAGE_SHORTAGE", "NEVER", "OVERHEATING", "FINE", "USAGE_PERIOD", "TWO_YEARS"));
        personas.put("256GB 노트북에서 용량이 자주 부족했던 Node 개발자", answers(
            "CURRENT_OS", "WINDOWS", "CURRENT_WINDOWS_CPU", "U", "CURRENT_STORAGE", "GB_256_OR_LESS",
            "PREFERRED_OS", "WINDOWS", "PROGRAMMING_LANGUAGE", "NODE_TYPESCRIPT", "IDE", "LIGHT_EDITOR",
            "AI_CODING_TOOL", "AI_EDITOR", "DEV_ENVIRONMENT_SETUP", "LOCAL_FEW", "SLOWDOWN", "SOMETIMES",
            "STORAGE_SHORTAGE", "OFTEN", "OVERHEATING", "FINE", "USAGE_PERIOD", "THREE_TO_FOUR_YEARS"));
        personas.put("HX 노트북에서도 발열을 겪는 Java 개발자", answers(
            "CURRENT_OS", "WINDOWS", "CURRENT_WINDOWS_CPU", "HX", "CURRENT_MEMORY", "GB_32",
            "CURRENT_STORAGE", "TB_1_OR_MORE", "PREFERRED_OS", "WINDOWS", "PROGRAMMING_LANGUAGE", "JAVA_FAMILY",
            "IDE", "JETBRAINS", "AI_CODING_TOOL", "IDE_EXTENSION", "DEV_ENVIRONMENT_SETUP", "LOCAL_MANY",
            "SLOWDOWN", "FINE", "STORAGE_SHORTAGE", "NEVER", "BUILD_WAIT", "FINE", "OVERHEATING", "OFTEN",
            "USAGE_PERIOD", "FIVE_PLUS_YEARS"));
        return personas;
    }

    // 질문 코드와 답을 번갈아 받는다. 여러 개를 고르는 답만 List 로 넘긴다
    private static Map<String, List<String>> answers(Object... questionAndOptions) {
        Map<String, List<String>> answers = new TreeMap<>();
        for (int i = 0; i < questionAndOptions.length; i += 2) {
            Object options = questionAndOptions[i + 1];
            answers.put((String) questionAndOptions[i],
                options instanceof List<?> list ? list.stream().map(String.class::cast).toList()
                    : List.of((String) options));
        }
        return answers;
    }

    @ParameterizedTest
    @EnumSource(UsagePurposeCode.class)
    void 모든_사용_목적은_항목마다_근거를_갖춘_권장_사양을_반환한다(UsagePurposeCode purpose) {
        // given & when
        List<RecommendedSpec> recommendedSpecs = recommendationService.recommend(purpose.name(), Map.of());

        // then
        assertThat(recommendedSpecs).isNotEmpty().allSatisfy(recommended -> {
            List<SpecValue> values = recommended.spec().values();
            assertThat(values).isNotEmpty();
            assertThat(values).allSatisfy(value ->
                assertThat(recommended.itemReasons().get(value.code()))
                    .isNotEmpty().allSatisfy(reason -> assertThat(reason).isNotBlank()));
        });
    }

    @Test
    void 존재하지_않는_사용_목적_코드면_예외가_발생한다() {
        // given
        String purposeCode = "NOT_EXIST";

        // when & then
        assertThatThrownBy(() -> recommendationService.recommend(purposeCode, Map.of()))
            .isInstanceOf(BusinessException.class)
            .extracting(thrown -> ((BusinessException) thrown).getErrorCode())
            .isEqualTo(BusinessErrorCode.USAGE_PURPOSE_NOT_FOUND);
    }

    @Test
    void 존재하지_않는_사용_목적_코드로_설문을_찾으면_예외가_발생한다() {
        // given
        String purposeCode = "NOT_EXIST";

        // when & then
        assertThatThrownBy(() -> recommendationService.findSurvey(purposeCode))
            .isInstanceOf(BusinessException.class)
            .extracting(thrown -> ((BusinessException) thrown).getErrorCode())
            .isEqualTo(BusinessErrorCode.USAGE_PURPOSE_NOT_FOUND);
    }

    @TestFactory
    Stream<DynamicTest> 스냅숏의_답변_조합으로_추천을_요청하면_스냅숏에_저장된_응답과_같은_응답을_반환한다() throws Exception {
        // given
        List<SnapshotCase> cases = readSnapshot();

        return cases.stream().map(snapshotCase -> dynamicTest(snapshotCase.displayName(), () -> {
            // when
            JsonNode response = recommend(snapshotCase.answers());

            // then
            assertThat(response).isEqualTo(snapshotCase.response());
        }));
    }

    /**
     * 규칙이나 근거 문장을 바꾼 PR 에서 스냅숏을 다시 만든다. 답변 조합은 그대로 두고 응답만 새로 적으므로,
     * 바뀐 결과가 diff 로 보인다.
     * <pre>
     * UPDATE_SNAPSHOT=true ./gradlew test --tests '*RecommendationServiceTest.스냅숏을*'
     * </pre>
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "UPDATE_SNAPSHOT", matches = "true")
    void 스냅숏을_현재_규칙의_응답으로_다시_만든다() throws Exception {
        // given
        List<SnapshotCase> cases = readSnapshot();

        // when
        List<SnapshotCase> updated = new ArrayList<>();
        for (SnapshotCase snapshotCase : cases) {
            updated.add(new SnapshotCase(snapshotCase.name(), snapshotCase.answers(), recommend(snapshotCase.answers())));
        }

        // then
        writeSnapshot(updated);
    }

    /**
     * 질문이나 선택지를 바꿔 기존 답변 조합이 맞지 않게 됐을 때 답변 조합부터 새로 만든다. 규칙만 바꿨다면
     * 결과에 따라 남는 조합이 달라져 diff 를 읽기 어려우므로 위의 UPDATE_SNAPSHOT 을 쓴다.
     * <p>
     * 화면에서 만들 수 있는 조합만 만든다 — 노출 조건을 지키고, 현재 사양 단계만 건너뛸 수 있다.
     * 결과 사양이 같은 조합은 몇 건만 남겨 파일 크기를 유지한다.
     * 제품 추천 피드백으로 더한 사례("피드백 #번호")는 회귀 테스트이므로 답변 조합을 그대로 남긴다.
     * <pre>
     * REGENERATE_SNAPSHOT=true ./gradlew test --tests '*RecommendationServiceTest.스냅숏의_답변_조합을*'
     * </pre>
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "REGENERATE_SNAPSHOT", matches = "true")
    void 스냅숏의_답변_조합을_노출_조건에_맞게_새로_만든다() throws Exception {
        // given
        Survey survey = recommendationService.findSurvey("BACKEND_DEVELOPMENT");
        Random random = new Random(SNAPSHOT_SEED);

        // when
        List<SnapshotCase> cases = new ArrayList<>();
        PERSONAS.forEach((name, answers) -> cases.add(new SnapshotCase(name, answers, recommend(answers))));
        List<SnapshotCase> feedbackCases = readSnapshot().stream()
            .filter(snapshotCase -> snapshotCase.name() != null && snapshotCase.name().startsWith(FEEDBACK_CASE_PREFIX))
            .map(snapshotCase -> new SnapshotCase(snapshotCase.name(), snapshotCase.answers(), recommend(snapshotCase.answers())))
            .toList();
        cases.addAll(feedbackCases);

        Set<Map<String, List<String>>> seen = new HashSet<>(PERSONAS.values());
        feedbackCases.forEach(snapshotCase -> seen.add(snapshotCase.answers()));
        Map<List<?>, Integer> countBySpecs = new HashMap<>();
        for (int i = 0; i < SNAPSHOT_ATTEMPTS; i++) {
            Map<String, List<String>> answers = randomAnswers(survey, random);
            if (!seen.add(answers)) {
                continue;
            }
            List<?> specs = recommendationService.recommend("BACKEND_DEVELOPMENT", answers).stream()
                .map(recommended -> List.of(recommended.spec().values(), recommended.preferred()))
                .toList();
            if (countBySpecs.merge(specs, 1, Integer::sum) <= CASES_PER_SPECS) {
                cases.add(new SnapshotCase(null, answers, recommend(answers)));
            }
        }

        // then
        writeSnapshot(cases);
    }

    private Map<String, List<String>> randomAnswers(Survey survey, Random random) {
        Map<String, List<String>> answers = new TreeMap<>();
        for (Step step : survey.steps()) {
            boolean skippable = "CURRENT_SPEC".equals(step.layout());
            for (Question question : step.questions()) {
                boolean shown = question.showWhen() == null
                    || question.showWhen().evaluate(new SurveyResponse(answers));
                if (shown && !(skippable && random.nextBoolean())) {
                    answers.put(question.code(), randomOptions(question, random));
                }
            }
        }
        return answers;
    }

    private List<String> randomOptions(Question question, Random random) {
        List<String> codes = question.options().stream().map(Option::code).toList();
        String picked = codes.get(random.nextInt(codes.size()));
        if (question.input() == InputType.SINGLE || picked.equals(question.exclusive())) {
            return List.of(picked);
        }
        return codes.stream()
            .filter(code -> !code.equals(question.exclusive()))
            .filter(code -> code.equals(picked) || random.nextInt(3) == 0)
            .toList();
    }

    private JsonNode recommend(Map<String, List<String>> answers) {
        return MAPPER.valueToTree(RecommendationResponse.from(
            recommendationService.recommend("BACKEND_DEVELOPMENT", answers)));
    }

    private List<SnapshotCase> readSnapshot() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(SNAPSHOT)) {
            return MAPPER.readValue(input, new TypeReference<>() {});
        }
    }

    private void writeSnapshot(List<SnapshotCase> cases) throws Exception {
        // 한 줄에 한 건씩 적어, 결과가 바뀐 건만 diff 에 나오게 한다
        List<String> lines = cases.stream().map(MAPPER::writeValueAsString).toList();
        Files.writeString(Path.of("src/test/resources" + SNAPSHOT), "[\n  " + String.join(",\n  ", lines) + "\n]\n");
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SnapshotCase(String name, Map<String, List<String>> answers, JsonNode response) {

        String displayName() {
            if (name == null) {
                return answers.toString();
            }
            return name;
        }
    }
}
