package com.wrb.devica.recommendation.service;

import com.wrb.devica.purpose.domain.UsagePurposeCode;
import com.wrb.devica.recommendation.domain.Answers;
import com.wrb.devica.recommendation.domain.RecommendationAlgorithm;
import com.wrb.devica.recommendation.domain.RecommendedSpec;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {

    private final Map<UsagePurposeCode, RecommendationAlgorithm> algorithms;

    public RecommendationService(List<RecommendationAlgorithm> algorithms) {
        this.algorithms = new EnumMap<>(UsagePurposeCode.class);
        algorithms.forEach(algorithm -> this.algorithms.put(algorithm.purpose(), algorithm));
    }

    public List<RecommendedSpec> recommend(String purposeCode, Map<String, List<String>> answers) {
        return algorithmOf(purposeCode).recommend(Answers.from(answers));
    }

    private RecommendationAlgorithm algorithmOf(String purposeCode) {
        UsagePurposeCode purpose = UsagePurposeCode.from(purposeCode);
        RecommendationAlgorithm algorithm = algorithms.get(purpose);
        if (algorithm == null) {
            throw new IllegalStateException("추천 알고리즘이 없는 사용 목적입니다: " + purpose);
        }
        return algorithm;
    }
}
