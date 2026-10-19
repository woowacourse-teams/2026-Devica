package com.wrb.devica.recommendation.domain;

import com.wrb.devica.purpose.domain.UsagePurposeCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecommendationDefinitions {

    private final Map<UsagePurposeCode, RecommendationDefinition> purposes;

    public RecommendationDefinitions(Map<UsagePurposeCode, RecommendationDefinition> purposes,
                                     Set<String> requirements, Map<String, Set<String>> conditions) {
        List<String> errors = new ArrayList<>();

        purposes.forEach((code, definition) ->
            new RecommendationDefinitionValidator(requirements, conditions).validate(definition)
                .forEach(error -> errors.add(code + ": " + error)));

        if (!errors.isEmpty()) {
            throw new IllegalStateException("추천 콘텐츠가 올바르지 않습니다.\n" + String.join("\n", errors));
        }

        this.purposes = Map.copyOf(purposes);
    }

    public RecommendationDefinition get(UsagePurposeCode code) {
        return purposes.get(code);
    }
}
