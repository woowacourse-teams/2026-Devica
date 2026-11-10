package com.wrb.devica.recommendation.domain;

import java.util.List;
import java.util.Map;

public record Requirements(Map<String, Integer> values, Map<String, List<String>> reasons, List<String> notes,
                           Map<String, String> basis) {

    public Requirements(Map<String, Integer> values, Map<String, List<String>> reasons, List<String> notes) {
        this(values, reasons, notes, Map.of());
    }

    public Requirements withBasis(Map<String, String> basis) {
        return new Requirements(values, reasons, notes, basis);
    }

    public int valueOf(String criterion) {
        return values.getOrDefault(criterion, 0);
    }

    public List<String> reasonsOf(String criterion) {
        return reasons.getOrDefault(criterion, List.of());
    }
}
