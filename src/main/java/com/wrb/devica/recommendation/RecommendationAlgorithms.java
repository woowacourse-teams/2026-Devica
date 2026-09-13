package com.wrb.devica.recommendation;

import com.wrb.devica.purpose.UsagePurposeCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RecommendationAlgorithms {

    private final Map<UsagePurposeCode, RecommendationAlgorithm> byPurpose;

    public RecommendationAlgorithms(List<RecommendationAlgorithm> algorithms) {
        this.byPurpose = new EnumMap<>(UsagePurposeCode.class);
        algorithms.forEach(algorithm -> byPurpose.put(algorithm.purpose(), algorithm));
    }

    /**
     * 담당 알고리즘이 없는 목적은 사용자 오류가 아니라 서버 설정 오류다.
     * 목적을 추가하고 구현체를 빠뜨리면 정합성 테스트가 배포 전에 잡는다.
     */
    public RecommendationAlgorithm findByPurpose(UsagePurposeCode purpose) {
        RecommendationAlgorithm algorithm = byPurpose.get(purpose);
        if (algorithm == null) {
            throw new IllegalStateException("담당 추천 알고리즘이 없는 사용 목적입니다: " + purpose);
        }
        return algorithm;
    }
}
