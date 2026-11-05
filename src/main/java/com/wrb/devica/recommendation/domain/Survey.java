package com.wrb.devica.recommendation.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wrb.devica.common.exception.BusinessErrorCode;
import com.wrb.devica.common.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record Survey(List<Step> steps, RetiredCodes retired, String version) {

    public Survey {
        if (retired == null) {
            retired = RetiredCodes.NONE;
        }
    }

    @JsonCreator
    public Survey(@JsonProperty("steps") List<Step> steps) {
        this(steps, RetiredCodes.NONE, null);
    }

    public Survey withVersion(String version) {
        return new Survey(steps, retired, version);
    }

    public Survey withRetired(RetiredCodes retired) {
        return new Survey(steps, retired, version);
    }

    public List<Question> questions() {
        return steps.stream()
            .flatMap(step -> step.questions().stream())
            .toList();
    }

    public Question question(String code) {
        return findQuestion(code)
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.QUESTION_NOT_FOUND));
    }

    public Optional<String> currentQuestionCode(String code, LocalDate today) {
        String current = code;
        while (findQuestion(current).isEmpty()) {
            RetiredCode line = retiredLine(current, null, today)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.QUESTION_NOT_FOUND));
            if (line.drop()) {
                return Optional.empty();
            }
            current = line.to();
        }
        return Optional.of(current);
    }

    public Optional<String> currentOptionCode(Question question, String option, LocalDate today) {
        if (question.hasOption(option)) {
            return Optional.of(option);
        }
        RetiredCode line = retiredLine(question.code(), option, today)
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.ANSWER_NOT_ALLOWED));
        return line.drop() ? Optional.empty() : Optional.of(line.to());
    }

    public boolean dropsInvalid(String questionCode, LocalDate today) {
        return retired.activeOn(today).anyMatch(line -> line.dropInvalid() && line.question().equals(questionCode));
    }

    private Optional<Question> findQuestion(String code) {
        return questions().stream()
            .filter(question -> question.code().equals(code))
            .findFirst();
    }

    private Optional<RetiredCode> retiredLine(String question, String option, LocalDate today) {
        return retired.activeOn(today)
            .filter(line -> !line.dropInvalid())
            .filter(line -> line.question().equals(question))
            .filter(line -> option == null ? line.isQuestionLine() : option.equals(line.option()))
            .findFirst();
    }
}
