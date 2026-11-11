package com.wrb.devica.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.wrb.devica.common.exception.BusinessException;
import com.wrb.devica.purpose.domain.UsagePurposeCode;
import com.wrb.devica.recommendation.config.RecommendationConfig;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RetiredCodeConversionTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, List<String>>> ANSWERS = new TypeReference<>() {};

    @TestFactory
    Stream<DynamicTest> 지운_코드가_든_답은_변환_사례표대로_읽는다() throws IOException {
        // given
        JsonNode table;
        try (InputStream input = getClass().getResourceAsStream("/recommendation/retired-cases.json")) {
            table = MAPPER.readTree(input);
        }
        Survey survey = MAPPER.convertValue(table.get("survey"), Survey.class)
            .withRetired(MAPPER.convertValue(table.get("retired"), RetiredCodes.class));

        return table.get("cases").valueStream().map(testCase -> dynamicTest(testCase.get("name").asString(), () -> {
            LocalDate today = LocalDate.parse(testCase.get("today").asString());
            Map<String, List<String>> params = MAPPER.convertValue(testCase.get("params"), ANSWERS);

            // when & then
            if (testCase.has("error")) {
                assertThatThrownBy(() -> SurveyResponse.from(params, survey, today))
                    .isInstanceOf(BusinessException.class)
                    .extracting(thrown -> ((BusinessException) thrown).getErrorCode().getCode())
                    .isEqualTo(testCase.get("error").asString());
                return;
            }

            SurveyResponse response = SurveyResponse.from(params, survey, today);
            assertThat(response.answers()).isEqualTo(MAPPER.convertValue(testCase.get("answers"), ANSWERS));
            assertThat(response.outdated()).isEqualTo(testCase.get("outdated").asBoolean());
        }));
    }

    @TestFactory
    Stream<DynamicTest> 정의서의_지운_코드는_보관_기간_동안만_옮겨_읽는다() {
        // given
        RecommendationDefinitions definitions = new RecommendationConfig().recommendationDefinitions();

        return Stream.of(UsagePurposeCode.values()).flatMap(purpose -> {
            Survey survey = definitions.get(purpose).survey();
            return survey.retired().codes().stream()
                .filter(line -> line.option() != null && line.to() != null)
                .map(line -> dynamicTest(purpose + " " + line.question() + "." + line.option(), () -> {
                    Map<String, List<String>> params = Map.of(line.question(), List.of(line.option()));
                    LocalDate lastDay = line.retiredAt().plusMonths(6);

                    // when
                    SurveyResponse response = SurveyResponse.from(params, survey, lastDay);

                    // then
                    assertThat(response.answers().get(line.question())).containsExactly(line.to());
                    assertThat(response.outdated()).isTrue();
                    assertThatThrownBy(() -> SurveyResponse.from(params, survey, lastDay.plusDays(1)))
                        .isInstanceOf(BusinessException.class)
                        .extracting(thrown -> ((BusinessException) thrown).getErrorCode().getCode())
                        .isEqualTo("ANSWER_NOT_ALLOWED");
                }));
        });
    }
}
