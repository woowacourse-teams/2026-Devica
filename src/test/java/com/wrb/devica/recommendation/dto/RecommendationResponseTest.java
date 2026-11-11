package com.wrb.devica.recommendation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.recommendation.domain.Recommendation;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RecommendationResponseTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void 바꿔_읽은_결과면_answersOutdated_를_참으로_담는다() {
        // given
        RecommendationResponse response = RecommendationResponse.from(new Recommendation(List.of(), true));

        // when
        JsonNode json = MAPPER.valueToTree(response);

        // then
        assertThat(json.get("answersOutdated").asBoolean()).isTrue();
    }

    @Test
    void 바꿔_읽지_않은_결과면_answersOutdated_를_담지_않는다() {
        // given
        RecommendationResponse response = RecommendationResponse.from(new Recommendation(List.of(), false));

        // when
        JsonNode json = MAPPER.valueToTree(response);

        // then
        assertThat(json.has("answersOutdated")).isFalse();
    }
}
