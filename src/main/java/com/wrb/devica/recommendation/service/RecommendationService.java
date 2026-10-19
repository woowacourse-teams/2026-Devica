package com.wrb.devica.recommendation.service;

import com.wrb.devica.purpose.domain.UsagePurposeCode;
import com.wrb.devica.recommendation.definition.RecommendationDefinition;
import com.wrb.devica.recommendation.definition.RecommendationDefinitions;
import com.wrb.devica.recommendation.domain.RecommendedSpec;
import com.wrb.devica.recommendation.judgement.Judgement;
import com.wrb.devica.recommendation.judgement.Requirements;
import com.wrb.devica.recommendation.laptop.LaptopRecommender;
import com.wrb.devica.recommendation.survey.SurveyResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationDefinitions recommendationDefinitions;
    private final LaptopRecommender laptopRecommender;

    public List<RecommendedSpec> recommend(String purposeCode, Map<String, List<String>> answers) {
        UsagePurposeCode usagePurposeCode = UsagePurposeCode.from(purposeCode);
        RecommendationDefinition purpose = recommendationDefinitions.get(usagePurposeCode);
        SurveyResponse response = SurveyResponse.from(answers, purpose.survey());

        Requirements requirements = Judgement.judge(purpose.rules(), response);
        Map<String, String> conditions = Judgement.map(purpose.mappings(), response);

        return laptopRecommender.recommend(requirements, conditions);
    }
}
