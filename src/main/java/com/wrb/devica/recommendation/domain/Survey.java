package com.wrb.devica.recommendation.domain;

import com.wrb.devica.common.exception.BusinessErrorCode;
import com.wrb.devica.common.exception.BusinessException;
import java.util.List;

public record Survey(List<Step> steps) {

    public List<Question> questions() {
        return steps.stream()
            .flatMap(step -> step.questions().stream())
            .toList();
    }

    public Question question(String code) {
        return questions().stream()
            .filter(question -> question.code().equals(code))
            .findFirst()
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.QUESTION_NOT_FOUND));
    }
}
