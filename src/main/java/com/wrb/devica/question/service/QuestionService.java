package com.wrb.devica.question.service;

import com.wrb.devica.purpose.domain.UsagePurposeCode;
import com.wrb.devica.question.domain.Question;
import com.wrb.devica.question.repository.QuestionRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;

    @Transactional(readOnly = true)
    public List<Question> findByPurposeCode(String purposeCode) {
        UsagePurposeCode purpose = UsagePurposeCode.from(purposeCode);
        return questionRepository.findAllByUsagePurpose_Code(purpose).stream()
            .sorted(Comparator.comparing(Question::getCode))
            .toList();
    }
}
