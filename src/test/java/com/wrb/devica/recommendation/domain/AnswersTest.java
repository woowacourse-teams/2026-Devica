package com.wrb.devica.recommendation.domain;

import static com.wrb.devica.question.domain.QuestionCode.BUILD_WAIT;
import static com.wrb.devica.question.domain.QuestionCode.CURRENT_MAC_CPU;
import static com.wrb.devica.question.domain.QuestionCode.CURRENT_OS;
import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.question.domain.OptionCode;
import com.wrb.devica.question.domain.QuestionCode;
import com.wrb.devica.question.domain.option.BuildWait;
import com.wrb.devica.question.domain.option.CurrentMacCpu;
import com.wrb.devica.question.domain.option.CurrentOs;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnswersTest {

    @Test
    void 의존이_충족되지_않은_답은_담지_않는다() {
        // when
        Answers macCpuOnWindows = new Answers(Map.<QuestionCode, List<OptionCode>>of(
            CURRENT_OS, List.of(CurrentOs.WINDOWS),
            CURRENT_MAC_CPU, List.of(CurrentMacCpu.PRO)));
        Answers buildWaitWithoutJava = new Answers(Map.<QuestionCode, List<OptionCode>>of(
            BUILD_WAIT, List.of(BuildWait.OFTEN)));

        // then
        assertThat(macCpuOnWindows.isAnswered(CURRENT_OS)).isTrue();
        assertThat(macCpuOnWindows.isAnswered(CURRENT_MAC_CPU)).isFalse();
        assertThat(buildWaitWithoutJava.isAnswered(BUILD_WAIT)).isFalse();
    }

    @Test
    void 의존이_충족된_답은_담는다() {
        // when
        Answers answers = new Answers(Map.<QuestionCode, List<OptionCode>>of(
            CURRENT_OS, List.of(CurrentOs.MACOS),
            CURRENT_MAC_CPU, List.of(CurrentMacCpu.PRO)));

        // then
        assertThat(answers.answerTo(CURRENT_MAC_CPU)).isEqualTo(CurrentMacCpu.PRO);
    }
}
