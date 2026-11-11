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

        for (Note note : definition.notes()) {
            validateCondition(note.when(), questions, "안내 '" + note.message() + "'", errors);
        }

        for (Basis basis : definition.basis()) {
            validateBasis(basis, errors);
        }

        List<RetiredCode> retired = definition.survey().retired().codes();
        Set<String> retiredKeys = new HashSet<>();

        for (RetiredCode line : retired) {
            validateRetiredLine(line, retired, retiredKeys, questions, errors);
        }

        return errors;
    }

    private void validateQuestionInOrder(Question question, Map<String, Question> before, List<String> errors) {
        validateQuestion(question, before, errors);

        if (before.put(question.code(), question) != null) {
            errors.add("질문 코드가 겹칩니다: " + question.code());
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

    private void validateRule(Rule rule, Map<String, Question> questions, List<String> errors) {
        String where = "규칙 '" + rule.reason() + "'";

        if (!requirements.contains(rule.criterion())) {
            errors.add(where + ": 없는 요구 항목입니다: " + rule.criterion());
        }

        validateCondition(rule.when(), questions, where, errors);
    }

    private void validateCondition(Condition condition,
                                   Map<String, Question> questions, String where, List<String> errors) {
        if (condition == null) {
            errors.add(where + ": 조건이 없습니다");
            return;
        }

        condition.selections().forEach(selected -> validateSelection(selected, questions, where, errors));
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

    private void validateBasis(Basis basis, List<String> errors) {
        String where = "근거 " + basis.item();

        if (basis.os() != null) {
            where += "(" + basis.os() + ")";
        }

        if (basis.summary() == null || basis.summary().isBlank()) {
            errors.add(where + ": 결론 문장이 없습니다");
        }

        if (basis.points() == null || basis.points().isEmpty() || basis.points().size() > 3) {
            errors.add(where + ": 요점은 1~3개여야 합니다");
        }

        if (basis.upgrade().size() > 2) {
            errors.add(where + ": 더 필요한 경우는 2개까지 적습니다");
        }
    }

    private void validateRetiredLine(RetiredCode line, List<RetiredCode> retired, Set<String> keys,
                                     Map<String, Question> questions, List<String> errors) {
        String where = line.where();

        if (line.retiredAt() == null) {
            errors.add(where + ": retiredAt 이 없습니다");
        }

        if (!keys.add(line.question() + "." + line.option() + "." + line.dropInvalid())) {
            errors.add(where + ": 같은 코드의 줄이 겹칩니다");
        }

        if (line.dropInvalid()) {
            validateDropInvalidLine(line, questions, where, errors);
            return;
        }

        if (line.drop() == (line.to() != null)) {
            errors.add(where + ": to 와 drop 중 하나만 씁니다");
        }

        if (line.isQuestionLine()) {
            validateQuestionLine(line, retired, questions, where, errors);
            return;
        }

        validateOptionLine(line, questions.get(line.question()), where, errors);
    }

    private void validateDropInvalidLine(RetiredCode line, Map<String, Question> questions, String where,
                                         List<String> errors) {
        if (!questions.containsKey(line.question())) {
            errors.add(where + ": 지금 질문이 아닙니다");
        }

        if (line.option() != null || line.to() != null || line.drop()) {
            errors.add(where + ": dropInvalid 줄에는 다른 값을 쓰지 않습니다");
        }
    }

    private void validateQuestionLine(RetiredCode line, List<RetiredCode> retired, Map<String, Question> questions,
                                      String where, List<String> errors) {
        if (questions.containsKey(line.question())) {
            errors.add(where + ": 지금 있는 질문입니다");
            return;
        }

        if (line.to() != null && !leadsToCurrentQuestion(line, retired, questions)) {
            errors.add(where + ": to 가 지금 질문으로 이어지지 않습니다: " + line.to());
        }
    }

    private boolean leadsToCurrentQuestion(RetiredCode line, List<RetiredCode> retired,
                                           Map<String, Question> questions) {
        Set<String> seen = new HashSet<>();
        RetiredCode next = line;

        while (next != null && next.to() != null && seen.add(next.question())) {
            if (questions.containsKey(next.to())) {
                return true;
            }
            String code = next.to();
            next = retired.stream()
                .filter(candidate -> candidate.isQuestionLine() && candidate.question().equals(code))
                .findFirst()
                .orElse(null);
        }

        return false;
    }

    private void validateOptionLine(RetiredCode line, Question question, String where, List<String> errors) {
        if (question == null) {
            errors.add(where + ": 지금 질문이 아닙니다");
            return;
        }

        if (question.hasOption(line.option())) {
            errors.add(where + ": 지금 있는 선택지입니다");
        }

        if (line.to() != null && !question.hasOption(line.to())) {
            errors.add(where + ": to 가 지금 선택지가 아닙니다: " + line.to());
        }
    }
}
