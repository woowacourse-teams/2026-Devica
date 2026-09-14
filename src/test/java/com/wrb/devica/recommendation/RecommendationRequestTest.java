package com.wrb.devica.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.question.QuestionCode;
import com.wrb.devica.question.option.PreferredOs;
import com.wrb.devica.question.option.ProgrammingLanguage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecommendationRequestTest {

    @Test
    void 질문_코드별로_고른_선택지를_담는다() {
        // given
        RecommendationRequest request = new RecommendationRequest(Map.of(
            "PREFERRED_OS", List.of("MACOS"),
            "PROGRAMMING_LANGUAGE", List.of("JAVA_FAMILY")));

        // when
        Answers answers = request.toAnswers();

        // then
        assertThat(answers.single(QuestionCode.PREFERRED_OS)).isEqualTo(PreferredOs.MACOS);
        assertThat(answers.has(QuestionCode.PROGRAMMING_LANGUAGE, ProgrammingLanguage.JAVA_FAMILY)).isTrue();
        assertThat(answers.single(QuestionCode.IDE)).isNull();
    }
}
