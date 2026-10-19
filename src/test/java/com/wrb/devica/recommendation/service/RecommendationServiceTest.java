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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
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
        assertThatThrownBy(() -> recommendationService.recommend("NOT_EXIST", Map.of()))
            .isInstanceOf(BusinessException.class)
            .extracting(thrown -> ((BusinessException) thrown).getErrorCode())
            .isEqualTo(BusinessErrorCode.USAGE_PURPOSE_NOT_FOUND);
    }

    @TestFactory
    Stream<DynamicTest> 스냅숏의_답변_조합으로_추천을_요청하면_스냅숏에_저장된_응답과_같은_응답을_반환한다() throws Exception {
        // given
        JsonMapper mapper = JsonMapper.builder().build();
        List<SnapshotCase> cases;

        try (InputStream input = getClass()
            .getResourceAsStream("/recommendation/backend-development-snapshot.json")) {
            cases = mapper.readValue(input, new TypeReference<>() {});
        }

        return cases.stream().map(snapshotCase -> dynamicTest(snapshotCase.answers().toString(), () -> {
            // when
            RecommendationResponse response = RecommendationResponse.from(
                recommendationService.recommend("BACKEND_DEVELOPMENT", snapshotCase.answers()));

            // then
            assertThat(mapper.<JsonNode>valueToTree(response)).isEqualTo(snapshotCase.response());
        }));
    }

    private record SnapshotCase(Map<String, List<String>> answers, JsonNode response) {
    }
}
