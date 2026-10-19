package com.wrb.devica.recommendation.domain;

import com.wrb.devica.common.exception.BusinessErrorCode;
import com.wrb.devica.common.exception.BusinessException;
import com.wrb.devica.recommendation.domain.Condition.Selected;
import java.util.List;

public record Question(
    String code,
    String title,
    String description,
    String label,
    InputType input,
    String exclusive,
    Selected showWhen,
    List<Option> options) {

    public Question {
        if (input == null) {
            input = InputType.SINGLE;
        }
    }

    public List<String> select(List<String> optionCodes) {
        if (optionCodes == null || optionCodes.isEmpty()) {
            return List.of();
        }

        if (input == InputType.SINGLE && optionCodes.size() > 1) {
            throw new BusinessException(BusinessErrorCode.ANSWER_NOT_ALLOWED);
        }

        if (!optionCodes.stream().allMatch(this::hasOption)) {
            throw new BusinessException(BusinessErrorCode.ANSWER_NOT_ALLOWED);
        }

        if (optionCodes.size() > 1 && optionCodes.contains(exclusive)) {
            throw new BusinessException(BusinessErrorCode.ANSWER_NOT_ALLOWED);
        }

        return List.copyOf(optionCodes);
    }

    public boolean hasOption(String optionCode) {
        return options.stream().anyMatch(option -> option.code().equals(optionCode));
    }
}
