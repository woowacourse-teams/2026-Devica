package com.wrb.devica.question;

import com.wrb.devica.purpose.UsagePurposeCode;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;

    public List<Question> findByPurposeCode(String purposeCode) {
        UsagePurposeCode purpose = UsagePurposeCode.from(purposeCode);
        return questionRepository.findAllWithOptionsByPurpose(purpose).stream()
            .sorted(Comparator.comparing(Question::getCode))
            .toList();
    }
}
