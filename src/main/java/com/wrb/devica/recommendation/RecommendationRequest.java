package com.wrb.devica.recommendation;

import com.wrb.devica.question.OptionCode;
import com.wrb.devica.question.QuestionCode;
import jakarta.validation.constraints.NotNull;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 서버가 상태를 갖지 않으므로 매번 전체 답변을 받는다.
 */
public record RecommendationRequest(@NotNull Map<String, List<String>> answers) {

    public Answers toAnswers() {
        Map<QuestionCode, List<OptionCode>> selected = new EnumMap<>(QuestionCode.class);
        answers.forEach((code, optionCodes) -> {
            QuestionCode question = QuestionCode.from(code);
            selected.put(question, question.select(optionCodes));
        });
        return new Answers(selected);
    }
}
