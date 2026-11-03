package com.wrb.devica.recommendation.domain;

import java.util.List;

public record ScoreRange<T>(Integer upTo, T value) {

    public static <T> T valueFor(List<ScoreRange<T>> ranges, int score) {
        return ranges.stream()
            .filter(range -> range.upTo() == null || score <= range.upTo())
            .findFirst()
            .orElseThrow()
            .value();
    }

    public static <T> void validate(List<ScoreRange<T>> ranges, String where, List<String> errors) {
        if (ranges == null || ranges.isEmpty()) {
            errors.add(where + ": 구간이 없습니다");
            return;
        }

        validateValues(ranges, where, errors);
        validateUpToPresence(ranges, where, errors);
        validateUpToOrder(ranges, where, errors);
    }

    private static <T> void validateValues(List<ScoreRange<T>> ranges, String where, List<String> errors) {
        for (int i = 0; i < ranges.size(); i++) {
            if (ranges.get(i).value() == null) {
                errors.add(where + ": " + (i + 1) + "번째 구간에 값이 없습니다");
            }
        }
    }

    private static <T> void validateUpToPresence(List<ScoreRange<T>> ranges, String where, List<String> errors) {
        int last = ranges.size() - 1;

        for (int i = 0; i < last; i++) {
            if (ranges.get(i).upTo() == null) {
                errors.add(where + ": 마지막이 아닌 구간에 upTo 가 없습니다");
            }
        }

        if (ranges.get(last).upTo() != null) {
            errors.add(where + ": 마지막 구간은 upTo 없이 나머지 점수를 모두 받아야 합니다");
        }
    }

    private static <T> void validateUpToOrder(List<ScoreRange<T>> ranges, String where, List<String> errors) {
        for (int i = 1; i < ranges.size() - 1; i++) {
            Integer previous = ranges.get(i - 1).upTo();
            Integer current = ranges.get(i).upTo();

            if (previous != null && current != null && current <= previous) {
                errors.add(where + ": upTo 는 작은 것부터 적어야 합니다");
            }
        }
    }
}
