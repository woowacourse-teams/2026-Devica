package com.wrb.devica.recommendation;

import com.wrb.devica.purpose.UsagePurposeCode;
import java.util.List;

/**
 * 사용 목적별 추천 규칙. 규칙을 바꾸려면 구현체를 갈아끼우고, 목적을 늘리려면 구현체를 하나 더 만든다.
 * <p>
 * 계약을 이 두 메서드보다 얇게 만들 수 없고, 현재 규칙의 세부가 계약에 올라오지 않는다.
 * 담당 목적을 구현체가 스스로 선언하므로 매핑이 컴파일 타임에 검증된다.
 */
public interface RecommendationAlgorithm {

    UsagePurposeCode purpose();

    List<RecommendedSpec> recommend(Answers answers);

    /**
     * 답변을 받기 전의 추천. 권장 사양 개념이 없는 목적은 빈 결과를 낸다.
     */
    default List<RecommendedSpec> recommend() {
        return recommend(Answers.empty());
    }
}
