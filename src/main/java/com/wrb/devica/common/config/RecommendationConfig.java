package com.wrb.devica.common.config;

import com.wrb.devica.purpose.domain.UsagePurposeCode;
import com.wrb.devica.recommendation.definition.RecommendationDefinition;
import com.wrb.devica.recommendation.definition.RecommendationDefinitions;
import com.wrb.devica.recommendation.laptop.LaptopCondition;
import com.wrb.devica.recommendation.laptop.LaptopRequirement;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class RecommendationConfig {

    private static final String PURPOSE_PATH = "recommendation/purposes/%s.yml";

    @Bean
    public RecommendationDefinitions recommendationDefinitions() {
        Map<UsagePurposeCode, RecommendationDefinition> purposes = new EnumMap<>(UsagePurposeCode.class);
        for (UsagePurposeCode code : UsagePurposeCode.values()) {
            purposes.put(code, read(code));
        }
        Set<String> requirements = Arrays.stream(LaptopRequirement.values())
            .map(Enum::name)
            .collect(Collectors.toSet());
        Map<String, Set<String>> conditions = Arrays.stream(LaptopCondition.values())
            .collect(Collectors.toMap(Enum::name, LaptopCondition::allowedValues));
        return new RecommendationDefinitions(purposes, requirements, conditions);
    }

    private RecommendationDefinition read(UsagePurposeCode code) {
        String path = PURPOSE_PATH.formatted(code);
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return RecommendationDefinition.read(input);
        } catch (IOException e) {
            throw new UncheckedIOException("추천 콘텐츠 파일을 읽지 못했습니다: " + path, e);
        }
    }
}
