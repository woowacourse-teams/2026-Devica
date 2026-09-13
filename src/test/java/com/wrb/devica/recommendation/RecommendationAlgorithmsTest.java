package com.wrb.devica.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrb.devica.purpose.UsagePurposeCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RecommendationAlgorithmsTest {

    @Autowired
    private RecommendationAlgorithms algorithms;

    // 목적을 추가하고 구현체를 빠뜨리면 그 목적의 추천 요청이 500 이 된다. 배포 전에 여기서 잡는다
    @ParameterizedTest
    @EnumSource(UsagePurposeCode.class)
    void 모든_사용_목적에_담당_알고리즘이_있다(UsagePurposeCode purpose) {
        // when
        RecommendationAlgorithm algorithm = algorithms.findByPurpose(purpose);

        // then
        assertThat(algorithm.purpose()).isEqualTo(purpose);
    }

    @Test
    void 담당_알고리즘이_없으면_예외가_발생한다() {
        // given
        RecommendationAlgorithms empty = new RecommendationAlgorithms(List.of());

        // when & then
        assertThatThrownBy(() -> empty.findByPurpose(UsagePurposeCode.BACKEND_DEVELOPMENT))
            .isInstanceOf(IllegalStateException.class);
    }
}
