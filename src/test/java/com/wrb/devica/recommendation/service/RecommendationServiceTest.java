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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
class RecommendationServiceTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String SNAPSHOT_DIRECTORY = "/recommendation/";
    private static final long SNAPSHOT_SEED = 242L;
    private static final int SNAPSHOT_ATTEMPTS = 20_000;
    private static final int CASES_PER_SPECS = 3;

    @Autowired
    private RecommendationService recommendationService;

    @ParameterizedTest
    @EnumSource(UsagePurposeCode.class)
    void 모든_사용_목적은_항목마다_기본_근거를_갖춘_권장_사양을_반환한다(UsagePurposeCode purpose) {
        // given & when
        List<RecommendedSpec> recommendedSpecs = recommendationService.recommend(purpose.name(), Map.of()).specs();

        // then
        assertThat(recommendedSpecs).isNotEmpty().allSatisfy(recommended -> {
            List<SpecValue> values = recommended.spec().values();
            assertThat(values).isNotEmpty();
            assertThat(values).allSatisfy(value ->
                assertThat(recommended.itemBasis().get(value.code())).isNotNull());
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
    Stream<DynamicTest> 스냅숏의_답변_조합으로_추천을_요청하면_스냅숏에_저장된_응답과_같은_응답을_반환한다() {
        // given
        Stream<UsagePurposeCode> purposes = Stream.of(UsagePurposeCode.values());

        return purposes.flatMap(purpose -> readSnapshot(purpose).stream()
            .map(snapshotCase -> dynamicTest(purpose + " " + snapshotCase.displayName(), () -> {
                // when
                JsonNode response = recommend(purpose, snapshotCase.answers());

                // then
                assertThat(response).isEqualTo(snapshotCase.response());
            })));
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
        for (UsagePurposeCode purpose : UsagePurposeCode.values()) {
            // given
            List<SnapshotCase> cases = readSnapshot(purpose);

            // when
            List<SnapshotCase> updated = cases.stream()
                .map(snapshotCase -> new SnapshotCase(
                    snapshotCase.name(), snapshotCase.answers(), recommend(purpose, snapshotCase.answers())))
                .toList();

            // then
            writeSnapshot(purpose, updated);
        }
    }

    /**
     * 질문이나 선택지를 바꿔 기존 답변 조합이 맞지 않게 됐을 때 답변 조합부터 새로 만든다. 규칙만 바꿨다면
     * 결과에 따라 남는 조합이 달라져 diff 를 읽기 어려우므로 위의 UPDATE_SNAPSHOT 을 쓴다.
     * <p>
     * 화면에서 만들 수 있는 조합만 만든다 — 노출 조건을 지키고, 현재 사양 단계만 건너뛸 수 있다.
     * 결과 사양이 같은 조합은 몇 건만 남겨 파일 크기를 유지한다.
     * 이름 있는 사례(대표 사용자, 제품 추천 피드백으로 더한 "피드백 #번호")는 회귀 테스트이므로 답변 조합을 그대로 남긴다.
     * <pre>
     * REGENERATE_SNAPSHOT=true ./gradlew test --tests '*RecommendationServiceTest.스냅숏의_답변_조합을*'
     * </pre>
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "REGENERATE_SNAPSHOT", matches = "true")
    void 스냅숏의_답변_조합을_노출_조건에_맞게_새로_만든다() throws Exception {
        for (UsagePurposeCode purpose : UsagePurposeCode.values()) {
            // given
            Survey survey = recommendationService.findSurvey(purpose.name());
            Random random = new Random(SNAPSHOT_SEED);

            // when
            List<SnapshotCase> cases = new ArrayList<>(readSnapshot(purpose).stream()
                .filter(snapshotCase -> snapshotCase.name() != null)
                .map(snapshotCase -> new SnapshotCase(
                    snapshotCase.name(), snapshotCase.answers(), recommend(purpose, snapshotCase.answers())))
                .toList());

            Set<Map<String, List<String>>> seen = new HashSet<>();
            cases.forEach(snapshotCase -> seen.add(snapshotCase.answers()));
            Map<List<?>, Integer> countBySpecs = new HashMap<>();
            for (int i = 0; i < SNAPSHOT_ATTEMPTS; i++) {
                Map<String, List<String>> answers = randomAnswers(survey, random);
                if (!seen.add(answers)) {
                    continue;
                }
                List<?> specs = recommendationService.recommend(purpose.name(), answers).specs().stream()
                    .map(recommended -> List.of(recommended.spec().values(), recommended.preferred()))
                    .toList();
                if (countBySpecs.merge(specs, 1, Integer::sum) <= CASES_PER_SPECS) {
                    cases.add(new SnapshotCase(null, answers, recommend(purpose, answers)));
                }
            }

            // then
            writeSnapshot(purpose, cases);
        }
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

    private JsonNode recommend(UsagePurposeCode purpose, Map<String, List<String>> answers) {
        JsonNode response = MAPPER.valueToTree(RecommendationResponse.from(
            recommendationService.recommend(purpose.name(), answers)));
        response.get("specs").forEach(spec -> spec.get("items").forEach(item -> ((ObjectNode) item).remove("basis")));
        return response;
    }

    private List<SnapshotCase> readSnapshot(UsagePurposeCode purpose) {
        try (InputStream input = getClass().getResourceAsStream(snapshotPath(purpose))) {
            if (input == null) {
                return List.of();
            }
            return MAPPER.readValue(input, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeSnapshot(UsagePurposeCode purpose, List<SnapshotCase> cases) throws Exception {
        // 한 줄에 한 건씩 적어, 결과가 바뀐 건만 diff 에 나오게 한다
        List<String> lines = cases.stream().map(MAPPER::writeValueAsString).toList();
        Files.writeString(Path.of("src/test/resources" + snapshotPath(purpose)),
            "[\n  " + String.join(",\n  ", lines) + "\n]\n");
    }

    private String snapshotPath(UsagePurposeCode purpose) {
        return SNAPSHOT_DIRECTORY + purpose.name().toLowerCase().replace('_', '-') + "-snapshot.json";
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
