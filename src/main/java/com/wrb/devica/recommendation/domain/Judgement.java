package com.wrb.devica.recommendation.domain;

import com.wrb.devica.product.domain.Os;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Judgement {

    private Judgement() {
    }

    public static Requirements judge(List<Rule> rules, List<Note> notes, SurveyResponse response, Os os) {
        Map<String, Integer> values = new HashMap<>();
        Map<String, List<String>> reasons = new HashMap<>();

        rules.stream()
            .filter(rule -> rule.appliesTo(os) && rule.when().evaluate(response))
            .forEach(rule -> addScore(rule, values, reasons));

        List<String> messages = notes.stream()
            .filter(note -> note.appliesTo(os) && note.when().evaluate(response))
            .map(Note::message)
            .toList();

        return new Requirements(values, reasons, messages);
    }

    public static Map<String, String> map(List<ConditionMapping> mappings, SurveyResponse response) {
        Map<String, String> conditions = new HashMap<>();

        mappings.stream()
            .filter(mapping -> mapping.when().evaluate(response))
            .forEach(mapping -> conditions.putIfAbsent(mapping.criterion(), mapping.value()));

        return conditions;
    }

    private static void addScore(Rule rule, Map<String, Integer> values, Map<String, List<String>> reasons) {
        values.merge(rule.criterion(), rule.weight(), Integer::sum);
        reasons.computeIfAbsent(rule.criterion(), criterion -> new ArrayList<>()).add(rule.reason());
    }
}
