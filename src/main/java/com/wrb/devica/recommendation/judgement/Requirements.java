package com.wrb.devica.recommendation.judgement;

import java.util.List;
import java.util.Map;

public record Requirements(Map<String, Integer> values, Map<String, List<String>> reasons) {

    public int valueOf(String criterion) {
        return values.getOrDefault(criterion, 0);
    }

    public List<String> reasonsOf(String criterion) {
        return reasons.getOrDefault(criterion, List.of());
    }
}
