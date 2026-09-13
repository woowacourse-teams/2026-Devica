package com.wrb.devica.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrb.devica.common.BusinessErrorCode;
import com.wrb.devica.common.BusinessException;
import com.wrb.devica.question.QuestionCode;
import com.wrb.devica.question.option.PreferredOs;
import com.wrb.devica.question.option.ProgrammingLanguage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecommendationRequestTest {

    @Test
    void 선택지_코드를_질문별_선택지로_바꾼다() {
        // given
        RecommendationRequest request = new RecommendationRequest(Map.of(
            "PREFERRED_OS", List.of("MACOS"),
            "PROGRAMMING_LANGUAGE", List.of("JAVA_FAMILY", "NODE_TYPESCRIPT")));

        // when
        Answers answers = request.toAnswers();

        // then
        assertThat(answers.single(QuestionCode.PREFERRED_OS)).isEqualTo(PreferredOs.MACOS);
        assertThat(answers.has(QuestionCode.PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY)).isTrue();
        assertThat(answers.has(QuestionCode.PROGRAMMING_LANGUAGE, ProgrammingLanguage.NODE_TYPESCRIPT)).isTrue();
    }

    @Test
    void 보내지_않은_질문은_답이_없다() {
        // when
        Answers answers = new RecommendationRequest(Map.of()).toAnswers();

        // then
        assertThat(answers.single(QuestionCode.PREFERRED_OS)).isNull();
    }

    @Test
    void 존재하지_않는_질문_코드면_예외가_발생한다() {
        // given
        RecommendationRequest request = new RecommendationRequest(Map.of("NOT_EXIST", List.of("MACOS")));

        // when & then
        assertThatThrownBy(request::toAnswers)
            .isInstanceOf(BusinessException.class)
            .extracting(thrown -> ((BusinessException) thrown).getErrorCode())
            .isEqualTo(BusinessErrorCode.QUESTION_NOT_FOUND);
    }

    @Test
    void 그_질문의_선택지가_아니면_예외가_발생한다() {
        // given - IDE 의 선택지를 PREFERRED_OS 에 보냈다
        RecommendationRequest request = new RecommendationRequest(Map.of("PREFERRED_OS", List.of("JETBRAINS")));

        // when & then
        assertThatThrownBy(request::toAnswers)
            .isInstanceOf(BusinessException.class)
            .extracting(thrown -> ((BusinessException) thrown).getErrorCode())
            .isEqualTo(BusinessErrorCode.ANSWER_NOT_ALLOWED);
    }

    @Test
    void 단일_선택_질문에_두_개를_보내면_예외가_발생한다() {
        // given
        RecommendationRequest request = new RecommendationRequest(
            Map.of("PREFERRED_OS", List.of("MACOS", "WINDOWS")));

        // when & then
        assertThatThrownBy(request::toAnswers)
            .isInstanceOf(BusinessException.class)
            .extracting(thrown -> ((BusinessException) thrown).getErrorCode())
            .isEqualTo(BusinessErrorCode.ANSWER_NOT_ALLOWED);
    }

    @Test
    void 배타_선택지를_다른_선택지와_함께_보내면_예외가_발생한다() {
        // given
        RecommendationRequest request = new RecommendationRequest(
            Map.of("PROGRAMMING_LANGUAGE", List.of("JAVA_FAMILY", "UNDECIDED")));

        // when & then
        assertThatThrownBy(request::toAnswers)
            .isInstanceOf(BusinessException.class)
            .extracting(thrown -> ((BusinessException) thrown).getErrorCode())
            .isEqualTo(BusinessErrorCode.ANSWER_NOT_ALLOWED);
    }
}
