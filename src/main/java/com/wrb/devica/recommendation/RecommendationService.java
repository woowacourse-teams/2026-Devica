package com.wrb.devica.recommendation;

import com.wrb.devica.purpose.UsagePurposeCode;
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

    public List<RecommendedSpec> findByPurposeCode(String purposeCode) {
        return algorithmOf(purposeCode).recommend(Answers.empty());
    }

    public List<RecommendedSpec> recommendByAnswers(String purposeCode, RecommendationRequest request) {
        return algorithmOf(purposeCode).recommend(request.toAnswers());
    }

    // 담당 알고리즘이 없는 목적은 사용자 오류가 아니라 서버 설정 오류다
    private RecommendationAlgorithm algorithmOf(String purposeCode) {
        UsagePurposeCode purpose = UsagePurposeCode.from(purposeCode);
        RecommendationAlgorithm algorithm = algorithms.get(purpose);
        if (algorithm == null) {
            throw new IllegalStateException("담당 추천 알고리즘이 없는 사용 목적입니다: " + purpose);
        }
        return algorithm;
    }
}
