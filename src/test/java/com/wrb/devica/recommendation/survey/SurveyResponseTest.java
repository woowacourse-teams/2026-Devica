package com.wrb.devica.recommendation.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrb.devica.common.exception.BusinessErrorCode;
import com.wrb.devica.common.exception.BusinessException;
import com.wrb.devica.recommendation.survey.Condition.Selected;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SurveyResponseTest {

    private static Survey surveyOf(Question... questions) {
        return new Survey(List.of(new Step(null, null, List.of(questions))));
    }

    private static Question question(String code, Selected showWhen, String... options) {
        return new Question(code, null, null, null, null, null, showWhen, optionsOf(options));
    }

    private static List<Option> optionsOf(String... codes) {
        return Arrays.stream(codes).map(code -> new Option(code, null, null)).toList();
    }

    @Test
    void 노출_조건을_만족하지_않는_질문의_답은_담지_않는다() {
        // given
        Survey survey = surveyOf(
            question("OS", null, "MAC", "WINDOWS"),
            question("MAC_CPU", new Selected("OS", List.of("MAC")), "BASIC", "PRO"),
            question("MEMORY", new Selected("MAC_CPU", List.of("PRO")), "GB_16", "GB_32"));
        Map<String, List<String>> params = Map.of(
            "OS", List.of("WINDOWS"),
            "MAC_CPU", List.of("PRO"),
            "MEMORY", List.of("GB_32"));

        // when
        SurveyResponse response = SurveyResponse.from(params, survey);

        // then
        assertThat(response.answers()).containsOnlyKeys("OS");
    }

    @Test
    void 노출_조건을_만족하면_답을_담는다() {
        // given
        Survey survey = surveyOf(
            question("OS", null, "MAC", "WINDOWS"),
            question("MAC_CPU", new Selected("OS", List.of("MAC")), "BASIC", "PRO"),
            question("MEMORY", new Selected("MAC_CPU", List.of("PRO")), "GB_16", "GB_32"));
        Map<String, List<String>> params = Map.of(
            "OS", List.of("MAC"),
            "MAC_CPU", List.of("PRO"),
            "MEMORY", List.of("GB_32"));

        // when
        SurveyResponse response = SurveyResponse.from(params, survey);

        // then
        assertThat(response.has("MEMORY", "GB_32")).isTrue();
    }

    @Test
    void 선택지를_보내지_않은_질문은_답하지_않은_것으로_본다() {
        // given
        Survey survey = surveyOf(question("OS", null, "MAC", "WINDOWS"));
        Map<String, List<String>> params = Map.of("OS", List.of());

        // when
        SurveyResponse response = SurveyResponse.from(params, survey);

        // then
        assertThat(response.answers()).isEmpty();
    }

    @Test
    void 설문에_없는_질문이면_예외가_발생한다() {
        // given
        Survey survey = surveyOf(question("OS", null, "MAC", "WINDOWS"));
        Map<String, List<String>> params = Map.of("NOT_EXIST", List.of("MAC"));

        // when & then
        assertThatThrownBy(() -> SurveyResponse.from(params, survey))
            .isInstanceOf(BusinessException.class)
            .extracting(thrown -> ((BusinessException) thrown).getErrorCode())
            .isEqualTo(BusinessErrorCode.QUESTION_NOT_FOUND);
    }

    @Test
    void 고를_수_없는_조합이면_예외가_발생한다() {
        // given
        Survey survey = surveyOf(
            question("OS", null, "MAC", "WINDOWS"),
            new Question("LANGUAGE", null, null, null, InputType.MULTI, "UNDECIDED", null,
                optionsOf("JAVA", "UNDECIDED")));
        List<Map<String, List<String>>> invalidParams = List.of(
            Map.of("OS", List.of("LINUX")),
            Map.of("OS", List.of("MAC", "WINDOWS")),
            Map.of("LANGUAGE", List.of("JAVA", "UNDECIDED")));

        // when & then
        assertThat(invalidParams).allSatisfy(params -> assertThatThrownBy(() -> SurveyResponse.from(params, survey))
            .isInstanceOf(BusinessException.class)
            .extracting(thrown -> ((BusinessException) thrown).getErrorCode())
            .isEqualTo(BusinessErrorCode.ANSWER_NOT_ALLOWED));
    }
}
