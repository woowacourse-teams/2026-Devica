package com.wrb.devica.recommendation.domain;

import com.wrb.devica.recommendation.domain.Condition.Selected;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

record RecommendationDefinitionValidator(Set<String> requirements, Map<String, Set<String>> conditions) {

    List<String> validate(RecommendationDefinition definition) {
        List<String> errors = new ArrayList<>();
        Map<String, Question> questions = new HashMap<>();

        for (Question question : definition.survey().questions()) {
            validateQuestionInOrder(question, questions, errors);
        }

        for (Rule rule : definition.rules()) {
            validateRule(rule, questions, errors);
        }

        for (ConditionMapping mapping : definition.mappings()) {
            validateMapping(mapping, questions, errors);
        }

        return errors;
    }

    private void validateQuestionInOrder(Question question, Map<String, Question> before, List<String> errors) {
        validateQuestion(question, before, errors);

        if (before.put(question.code(), question) != null) {
            errors.add("질문 코드가 겹칩니다: " + question.code());
        }
    }

    private void validateRule(Rule rule, Map<String, Question> questions, List<String> errors) {
        String where = "규칙 '" + rule.reason() + "'";

        if (!requirements.contains(rule.criterion())) {
            errors.add(where + ": 없는 요구 항목입니다: " + rule.criterion());
        }

        validateCondition(rule.when(), questions, where, errors);
    }

    private void validateMapping(ConditionMapping mapping, Map<String, Question> questions, List<String> errors) {
        String where = "조건 연결 " + mapping.criterion() + "=" + mapping.value();
        validateMappingValue(mapping, where, errors);
        validateCondition(mapping.when(), questions, where, errors);
    }

    private void validateMappingValue(ConditionMapping mapping, String where, List<String> errors) {
        Set<String> values = conditions.get(mapping.criterion());

        if (values == null) {
            errors.add(where + ": 없는 조건 항목입니다");
            return;
        }

        if (!values.contains(mapping.value())) {
            errors.add(where + ": 항목이 가질 수 없는 값입니다");
        }
    }

    private void validateQuestion(Question question, Map<String, Question> before, List<String> errors) {
        String where = "질문 " + question.code();
        validateOptionCodes(question, where, errors);

        if (question.exclusive() != null && !question.hasOption(question.exclusive())) {
            errors.add(where + ": 배타 선택지가 선택지에 없습니다: " + question.exclusive());
        }

        if (question.showWhen() != null) {
            validateSelection(question.showWhen(), before, where + " 의 노출 조건", errors);
        }
    }

    private void validateOptionCodes(Question question, String where, List<String> errors) {
        Set<String> optionCodes = new HashSet<>();

        question.options().stream()
            .map(Option::code)
            .filter(code -> !optionCodes.add(code))
            .forEach(code -> errors.add(where + ": 선택지 코드가 겹칩니다: " + code));
    }

    private void validateCondition(Condition condition,
                                   Map<String, Question> questions, String where, List<String> errors) {
        if (condition == null) {
            errors.add(where + ": 조건이 없습니다");
            return;
        }

        condition.selections().forEach(selected -> validateSelection(selected, questions, where, errors));
    }

    private void validateSelection(Selected selected,
                                   Map<String, Question> questions, String where, List<String> errors) {
        Question question = questions.get(selected.question());

        if (question == null) {
            errors.add(where + ": 가리킬 수 없는 질문입니다: " + selected.question());
            return;
        }

        selected.anyOf().stream()
            .filter(option -> !question.hasOption(option))
            .forEach(option -> errors.add(where + ": " + question.code() + " 에 없는 선택지입니다: " + option));
    }
}
