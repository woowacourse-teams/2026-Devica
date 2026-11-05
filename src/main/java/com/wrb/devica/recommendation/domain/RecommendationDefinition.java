package com.wrb.devica.recommendation.domain;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public record RecommendationDefinition(Survey survey, List<Rule> rules, List<ConditionMapping> mappings,
                                       List<Note> notes, LaptopInterpretation interpretation) {

    private static final JsonMapper MAPPER = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    public RecommendationDefinition {
        if (notes == null) {
            notes = List.of();
        }
    }

    public static RecommendationDefinition read(InputStream input) {
        try {
            byte[] bytes = input.readAllBytes();
            RecommendationDefinition definition =
                MAPPER.convertValue(new Yaml().load(new String(bytes, StandardCharsets.UTF_8)),
                    RecommendationDefinition.class);
            Survey survey = definition.survey().withVersion(versionOf(bytes));
            return new RecommendationDefinition(survey, definition.rules(), definition.mappings(), definition.notes(),
                definition.interpretation());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public RecommendationDefinition withRetired(RetiredCodes retired) {
        return new RecommendationDefinition(survey.withRetired(retired), rules, mappings, notes, interpretation);
    }

    private static String versionOf(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
