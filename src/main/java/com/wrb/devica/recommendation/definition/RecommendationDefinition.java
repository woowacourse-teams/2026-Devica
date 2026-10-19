package com.wrb.devica.recommendation.definition;

import com.wrb.devica.recommendation.judgement.ConditionMapping;
import com.wrb.devica.recommendation.judgement.Rule;
import com.wrb.devica.recommendation.survey.Survey;
import java.io.InputStream;
import java.util.List;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public record RecommendationDefinition(Survey survey, List<Rule> rules, List<ConditionMapping> mappings) {

    private static final JsonMapper MAPPER = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    public static RecommendationDefinition read(InputStream input) {
        return MAPPER.convertValue(new Yaml().load(input), RecommendationDefinition.class);
    }
}
