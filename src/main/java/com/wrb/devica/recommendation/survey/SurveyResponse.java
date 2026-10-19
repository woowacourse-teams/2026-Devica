package com.wrb.devica.recommendation.survey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SurveyResponse(Map<String, List<String>> answers) {

    public SurveyResponse {
        answers = Map.copyOf(answers);
    }

    public static SurveyResponse from(Map<String, List<String>> params, Survey survey) {
        Map<String, List<String>> selected = new HashMap<>();

        params.forEach((code, optionCodes) ->
            addIfAnswered(code, survey.question(code).select(optionCodes), selected));

        Map<String, List<String>> visible = new HashMap<>();

        for (Question question : survey.questions()) {
            addIfShown(question, selected, visible);
        }

        return new SurveyResponse(visible);
    }

    private static void addIfAnswered(String code, List<String> options, Map<String, List<String>> selected) {
        if (!options.isEmpty()) {
            selected.put(code, options);
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
