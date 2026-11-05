package com.wrb.devica.recommendation.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.purpose.domain.UsagePurposeCode;
import com.wrb.devica.recommendation.domain.RecommendationDefinitions;
import com.wrb.devica.recommendation.domain.RetiredCodes;
import org.junit.jupiter.api.Test;

class RecommendationConfigTest {

    @Test
    void 지운_코드_목록_파일이_없는_사용_목적은_빈_목록으로_읽는다() {
        // when
        RecommendationDefinitions definitions = new RecommendationConfig().recommendationDefinitions();

        // then
        for (UsagePurposeCode code : UsagePurposeCode.values()) {
            assertThat(definitions.get(code).survey().retired()).isEqualTo(RetiredCodes.NONE);
        }
    }
}
