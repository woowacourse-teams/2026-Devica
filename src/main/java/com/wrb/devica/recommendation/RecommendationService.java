package com.wrb.devica.recommendation;

import com.wrb.devica.purpose.UsagePurposeCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationAlgorithms algorithms;

    public List<RecommendedSpec> findByPurposeCode(String purposeCode) {
        return algorithmOf(purposeCode).recommend();
    }

    public List<RecommendedSpec> recommendByAnswers(String purposeCode, RecommendationRequest request) {
        return algorithmOf(purposeCode).recommend(request.toAnswers());
    }

    private RecommendationAlgorithm algorithmOf(String purposeCode) {
        UsagePurposeCode purpose = UsagePurposeCode.from(purposeCode);
        return algorithms.findByPurpose(purpose);
    }
}
