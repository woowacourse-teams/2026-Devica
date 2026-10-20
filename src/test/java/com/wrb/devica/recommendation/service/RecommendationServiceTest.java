package com.wrb.devica.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.wrb.devica.common.exception.BusinessErrorCode;
import com.wrb.devica.common.exception.BusinessException;
import com.wrb.devica.product.domain.SpecValue;
import com.wrb.devica.purpose.domain.UsagePurposeCode;
import com.wrb.devica.recommendation.domain.RecommendedSpec;
import com.wrb.devica.recommendation.dto.RecommendationResponse;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    @Autowired
    private RecommendationService recommendationService;

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
        JsonMapper mapper = JsonMapper.builder().build();
        List<SnapshotCase> cases = readSnapshot(mapper, "/recommendation/backend-development-snapshot.json");

        return cases.stream().map(snapshotCase -> dynamicTest(snapshotCase.answers().toString(), () -> {
            // when
            RecommendationResponse response = RecommendationResponse.from(
                recommendationService.recommend("BACKEND_DEVELOPMENT", snapshotCase.answers()));

            // then
            assertThat(mapper.<JsonNode>valueToTree(response)).isEqualTo(snapshotCase.response());
        }));
    }

    /**
     * 규칙을 바꾼 PR 에서 스냅숏을 다시 만든다. 답변 조합은 그대로 두고 응답만 새로 적으므로, 바뀐 결과가 diff 로 보인다.
     * <pre>
     * UPDATE_SNAPSHOT=true ./gradlew test --tests '*RecommendationServiceTest.스냅숏을*'
     * </pre>
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "UPDATE_SNAPSHOT", matches = "true")
    void 스냅숏을_현재_규칙의_응답으로_다시_만든다() throws Exception {
        // given
        JsonMapper mapper = JsonMapper.builder().build();
        String snapshot = "/recommendation/backend-development-snapshot.json";
        List<SnapshotCase> cases = readSnapshot(mapper, snapshot);

        // when
        List<String> lines = new ArrayList<>();
        for (SnapshotCase snapshotCase : cases) {
            RecommendationResponse response = RecommendationResponse.from(
                recommendationService.recommend("BACKEND_DEVELOPMENT", snapshotCase.answers()));
            lines.add(mapper.writeValueAsString(
                new SnapshotCase(snapshotCase.answers(), mapper.valueToTree(response))));
        }

        // then
        // 한 줄에 한 건씩 적어, 결과가 바뀐 건만 diff 에 나오게 한다
        Files.writeString(Path.of("src/test/resources" + snapshot),
            "[\n  " + String.join(",\n  ", lines) + "\n]\n");
    }

    private List<SnapshotCase> readSnapshot(JsonMapper mapper, String path) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            return mapper.readValue(input, new TypeReference<>() {});
        }
    }

    private record SnapshotCase(Map<String, List<String>> answers, JsonNode response) {
    }
}
