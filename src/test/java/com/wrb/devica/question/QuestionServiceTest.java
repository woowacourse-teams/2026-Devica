package com.wrb.devica.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrb.devica.common.BusinessErrorCode;
import com.wrb.devica.common.BusinessException;
import com.wrb.devica.common.FlywayTestConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("flyway-test")
@Import(FlywayTestConfiguration.class)
@Transactional
class QuestionServiceTest {

    @Autowired
    private QuestionService questionService;

    @Test
    void 사용_목적의_질문을_enum_선언_순서대로_반환한다() {
        // when
        List<Question> questions = questionService.findByPurposeCode("BACKEND_DEVELOPMENT");

        // then
        assertThat(questions).extracting(Question::getCode).containsExactly(QuestionCode.values());
    }

    @Test
    void 존재하지_않는_사용_목적_코드면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> questionService.findByPurposeCode("NOT_EXIST"))
            .isInstanceOf(BusinessException.class)
            .extracting(thrown -> ((BusinessException) thrown).getErrorCode())
            .isEqualTo(BusinessErrorCode.USAGE_PURPOSE_NOT_FOUND);
    }
}
