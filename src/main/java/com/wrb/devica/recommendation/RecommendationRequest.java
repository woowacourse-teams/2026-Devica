package com.wrb.devica.recommendation;

import com.wrb.devica.common.BusinessErrorCode;
import com.wrb.devica.common.BusinessException;
import com.wrb.devica.question.OptionCode;
import com.wrb.devica.question.QuestionCode;
import com.wrb.devica.question.QuestionInputType;
import jakarta.validation.constraints.NotNull;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 질문 코드에 고른 선택지 코드를 담아 보낸다. 건너뛴 질문은 키를 빼면 된다.
 * 서버는 상태를 갖지 않으므로 매번 전체 답변을 받는다.
 */
public record RecommendationRequest(@NotNull Map<String, List<String>> answers) {

    public Answers toAnswers() {
        Map<QuestionCode, List<OptionCode>> selected = new EnumMap<>(QuestionCode.class);
        answers.forEach((questionCode, optionCodes) -> {
            QuestionCode question = toQuestion(questionCode);
            selected.put(question, toOptions(question, optionCodes));
        });
        return new Answers(selected);
    }

    private QuestionCode toQuestion(String code) {
        try {
            return QuestionCode.valueOf(code);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(BusinessErrorCode.QUESTION_NOT_FOUND);
        }
    }

    private List<OptionCode> toOptions(QuestionCode question, List<String> optionCodes) {
        if (optionCodes == null || optionCodes.isEmpty()) {
            return List.of();
        }
        if (question.getInputType() == QuestionInputType.SINGLE && optionCodes.size() > 1) {
            throw new BusinessException(BusinessErrorCode.ANSWER_NOT_ALLOWED);
        }
        List<OptionCode> options = optionCodes.stream().map(code -> toOption(question, code)).toList();
        if (options.size() > 1 && options.contains(question.getExclusiveOption())) {
            throw new BusinessException(BusinessErrorCode.ANSWER_NOT_ALLOWED);
        }
        return options;
    }

    private OptionCode toOption(QuestionCode question, String code) {
        return question.getOptions().stream()
            .filter(option -> option.name().equals(code))
            .findFirst()
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.ANSWER_NOT_ALLOWED));
    }
}
