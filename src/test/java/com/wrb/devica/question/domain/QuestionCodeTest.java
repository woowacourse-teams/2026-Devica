package com.wrb.devica.question.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrb.devica.common.exception.BusinessErrorCode;
import com.wrb.devica.common.exception.BusinessException;
import com.wrb.devica.question.domain.option.ProgrammingLanguage;
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

    @Test
    void 코드로_질문을_찾고_없으면_예외가_발생한다() {
        // when & then
        assertThat(QuestionCode.from("PREFERRED_OS")).isEqualTo(QuestionCode.PREFERRED_OS);
        assertThatThrownBy(() -> QuestionCode.from("NOT_EXIST"))
            .isInstanceOf(BusinessException.class)
            .extracting(thrown -> ((BusinessException) thrown).getErrorCode())
            .isEqualTo(BusinessErrorCode.QUESTION_NOT_FOUND);
    }

    @Test
    void 보내온_코드를_그_질문의_선택지로_해석한다() {
        // when & then
        assertThat(QuestionCode.PROGRAMMING_LANGUAGE.select(List.of("JAVA_FAMILY", "NODE_TYPESCRIPT")))
            .containsExactly(ProgrammingLanguage.JAVA_FAMILY, ProgrammingLanguage.NODE_TYPESCRIPT);
        assertThat(QuestionCode.PREFERRED_OS.select(List.of())).isEmpty();
    }

    @Test
    void 고를_수_없는_조합이면_예외가_발생한다() {
        // when & then - 그 질문의 선택지가 아님 / 단일 선택에 복수 / 배타 선택지를 다른 것과 함께
        assertThat(List.of(
            select(QuestionCode.PREFERRED_OS, "JETBRAINS"),
            select(QuestionCode.PREFERRED_OS, "MACOS", "WINDOWS"),
            select(QuestionCode.PROGRAMMING_LANGUAGE, "JAVA_FAMILY", "UNDECIDED")))
            .allSatisfy(thrown -> assertThatThrownBy(thrown::run)
                .isInstanceOf(BusinessException.class)
                .extracting(caught -> ((BusinessException) caught).getErrorCode())
                .isEqualTo(BusinessErrorCode.ANSWER_NOT_ALLOWED));
    }

    private Runnable select(QuestionCode question, String... optionCodes) {
        return () -> question.select(List.of(optionCodes));
    }
}
