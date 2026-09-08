package com.wrb.devica.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class QuestionCodeTest {

    @Test
    void 배타_선택지는_그_질문의_선택지_중_하나다() {
        // given
        List<QuestionCode> questions = Arrays.stream(QuestionCode.values())
            .filter(question -> question.getExclusiveOption() != null)
            .toList();

        // when & then
        assertThat(questions).isNotEmpty().allSatisfy(question ->
            assertThat(question.getOptions()).contains(question.getExclusiveOption()));
    }

    @Test
    void 의존_조건의_선택지는_대상_질문의_선택지_중_하나다() {
        // given
        List<QuestionDependency> dependencies = Arrays.stream(QuestionCode.values())
            .map(QuestionCode::getDependency)
            .filter(Objects::nonNull)
            .toList();

        // when & then
        assertThat(dependencies).isNotEmpty().allSatisfy(dependency ->
            assertThat(dependency.question().getOptions()).contains(dependency.option()));
    }
}
