package com.wrb.devica.recommendation.domain;

import com.wrb.devica.purpose.domain.UsagePurposeCode;
import java.util.List;

/**
 * 사용 목적별 추천 규칙. 규칙을 바꾸려면 구현체를 갈아끼우고, 목적을 늘리려면 구현체를 하나 더 만든다.
 */
public interface RecommendationAlgorithm {

    UsagePurposeCode purpose();

    List<RecommendedSpec> recommend(Answers answers);
}
