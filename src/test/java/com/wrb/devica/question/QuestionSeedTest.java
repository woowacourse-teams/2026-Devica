package com.wrb.devica.question;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.purpose.UsagePurposeCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class QuestionSeedTest {

    @Autowired
    private QuestionRepository questionRepository;

    @Test
    void 시드된_질문과_enum이_일대일로_대응한다() {
        // when
        List<Question> seeded = questionRepository.findAllWithOptionsByPurpose(UsagePurposeCode.BACKEND_DEVELOPMENT);

        // then
        assertThat(seeded).extracting(Question::getCode).containsExactlyInAnyOrder(QuestionCode.values());
    }

    @Test
    void 질문마다_시드된_선택지와_enum_선택지가_일대일로_대응한다() {
        // when
        List<Question> seeded = questionRepository.findAllWithOptionsByPurpose(UsagePurposeCode.BACKEND_DEVELOPMENT);

        // then
        assertThat(seeded).allSatisfy(question -> {
            List<String> declared = question.getCode().getOptions().stream().map(OptionCode::name).toList();
            assertThat(question.getOptions()).extracting(QuestionOption::getCode)
                .containsExactlyInAnyOrderElementsOf(declared);
        });
    }
}
