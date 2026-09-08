package com.wrb.devica.question;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.purpose.UsagePurposeCode;
import com.wrb.devica.question.QuestionResponse.DependencyResponse;
import com.wrb.devica.question.QuestionResponse.OptionResponse;
import com.wrb.devica.question.option.CurrentOs;
import com.wrb.devica.question.option.PreferredOs;
import com.wrb.devica.question.option.ProgrammingLanguage;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class QuestionResponseTest {

    @Autowired
    private QuestionRepository questionRepository;

    @Test
    void 선택지를_enum_선언_순서대로_놓는다() {
        // when
        QuestionResponse response = QuestionResponse.from(findByCode(QuestionCode.PREFERRED_OS));

        // then
        assertThat(response.options()).extracting(OptionResponse::code)
            .containsExactly(PreferredOs.MACOS.name(), PreferredOs.WINDOWS.name(), PreferredOs.UNDECIDED.name());
    }

    @Test
    void 선택지의_문구는_시드된_행에서_가져온다() {
        // given
        Question question = findByCode(QuestionCode.PREFERRED_OS);
        Map<String, String> seeded = question.getOptions().stream()
            .collect(Collectors.toMap(QuestionOption::getCode, QuestionOption::getContent));

        // when
        QuestionResponse response = QuestionResponse.from(question);

        // then
        assertThat(response.options()).isNotEmpty().allSatisfy(option ->
            assertThat(option.content()).isEqualTo(seeded.get(option.code())));
    }

    @Test
    void 배타로_선언한_선택지는_exclusive_속성_표시가_붙는다() {
        // when
        QuestionResponse response = QuestionResponse.from(findByCode(QuestionCode.PROGRAMMING_LANGUAGE));

        // then
        assertThat(response.options()).filteredOn(OptionResponse::exclusive)
            .extracting(OptionResponse::code)
            .containsExactly(ProgrammingLanguage.UNDECIDED.name());
    }

    @Test
    void 조건부_노출_질문은_노출_조건으로_대상_질문과_선택지를_담는다() {
        // when
        QuestionResponse response = QuestionResponse.from(findByCode(QuestionCode.CURRENT_MAC_CPU));

        // then
        assertThat(response.dependsOn())
            .isEqualTo(new DependencyResponse(QuestionCode.CURRENT_OS.name(), CurrentOs.MACOS.name()));
    }

    private Question findByCode(QuestionCode code) {
        return questionRepository.findAllWithOptionsByPurpose(UsagePurposeCode.BACKEND_DEVELOPMENT).stream()
            .filter(question -> question.getCode() == code)
            .findFirst()
            .orElseThrow();
    }
}
