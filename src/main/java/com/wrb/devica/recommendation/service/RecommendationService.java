package com.wrb.devica.recommendation.service;

import com.wrb.devica.purpose.domain.UsagePurposeCode;
import com.wrb.devica.recommendation.domain.Judgement;
import com.wrb.devica.recommendation.domain.LaptopRecommender;
import com.wrb.devica.recommendation.domain.Recommendation;
import com.wrb.devica.recommendation.domain.RecommendationDefinition;
import com.wrb.devica.recommendation.domain.RecommendationDefinitions;
import com.wrb.devica.recommendation.domain.RecommendedSpec;
import com.wrb.devica.recommendation.domain.Survey;
import com.wrb.devica.recommendation.domain.SurveyResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationDefinitions recommendationDefinitions;
    private final LaptopRecommender laptopRecommender;
    private final Clock clock;

    public Recommendation recommend(String purposeCode, Map<String, List<String>> answers) {
        UsagePurposeCode usagePurposeCode = UsagePurposeCode.from(purposeCode);
        RecommendationDefinition purpose = recommendationDefinitions.get(usagePurposeCode);
        SurveyResponse response = SurveyResponse.from(answers, purpose.survey(), LocalDate.now(clock));

        Map<String, String> conditions = Judgement.map(purpose.mappings(), response);

        List<RecommendedSpec> specs = laptopRecommender.recommend(purpose.interpretation(),
            os -> Judgement.judge(purpose.rules(), purpose.notes(), response, os), conditions);

        return new Recommendation(specs, response.outdated());
    }

    public Survey findSurvey(String purposeCode) {
        return recommendationDefinitions.get(UsagePurposeCode.from(purposeCode)).survey();
    }
}
