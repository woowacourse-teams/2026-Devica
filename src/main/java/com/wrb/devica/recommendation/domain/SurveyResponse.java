package com.wrb.devica.recommendation.domain;

import com.wrb.devica.common.exception.BusinessException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record SurveyResponse(Map<String, List<String>> answers, boolean outdated) {

    public SurveyResponse {
        answers = Map.copyOf(answers);
    }

    public SurveyResponse(Map<String, List<String>> answers) {
        this(answers, false);
    }

    public static SurveyResponse from(Map<String, List<String>> params, Survey survey) {
        Map<String, Set<String>> current = new LinkedHashMap<>();
        Set<String> givenExclusive = new HashSet<>();
        boolean converted = false;

        for (Map.Entry<String, List<String>> param : params.entrySet()) {
            converted |= readInto(param.getKey(), param.getValue(), survey, current, givenExclusive);
        }

        Map<String, List<String>> selected = new HashMap<>();

        for (Map.Entry<String, Set<String>> answer : current.entrySet()) {
            converted |= selectInto(survey, answer.getKey(), answer.getValue(), givenExclusive, selected);
        }

        Map<String, List<String>> visible = new HashMap<>();

        for (Question question : survey.questions()) {
            addIfShown(question, selected, visible);
        }

        return new SurveyResponse(visible, converted || visible.size() < selected.size());
    }

    private static boolean readInto(String code, List<String> options, Survey survey,
                                    Map<String, Set<String>> current, Set<String> givenExclusive) {
        Optional<String> questionCode = survey.currentQuestionCode(code);
        boolean converted = !questionCode.equals(Optional.of(code));

        if (questionCode.isEmpty() || options == null) {
            return converted;
        }

        Question question = survey.question(questionCode.get());

        for (String option : options) {
            if (option.equals(question.exclusive())) {
                givenExclusive.add(question.code());
            }
            Optional<String> optionCode = survey.currentOptionCode(question, option);
            converted |= !optionCode.equals(Optional.of(option));
            optionCode.ifPresent(found ->
                current.computeIfAbsent(question.code(), key -> new LinkedHashSet<>()).add(found));
        }

        return converted;
    }

    private static boolean selectInto(Survey survey, String code, Set<String> options, Set<String> givenExclusive,
                                      Map<String, List<String>> selected) {
        Question question = survey.question(code);
        boolean absorbedExclusive = question.input() == InputType.MULTI && options.size() > 1
            && options.contains(question.exclusive()) && !givenExclusive.contains(code);

        if (absorbedExclusive) {
            options.remove(question.exclusive());
        }

        try {
            selected.put(code, question.select(List.copyOf(options)));
            return absorbedExclusive;
        } catch (BusinessException e) {
            if (survey.dropsInvalid(code)) {
                return true;
            }
            throw e;
        }
    }

    private static void addIfShown(Question question, Map<String, List<String>> selected,
                                   Map<String, List<String>> visible) {
        List<String> options = selected.get(question.code());

        if (options != null && isShown(question, visible)) {
            visible.put(question.code(), options);
        }
    }

    private static boolean isShown(Question question, Map<String, List<String>> answeredBefore) {
        return question.showWhen() == null || question.showWhen().evaluate(new SurveyResponse(answeredBefore));
    }

    public boolean has(String question, String option) {
        return answers.getOrDefault(question, List.of()).contains(option);
    }
}
