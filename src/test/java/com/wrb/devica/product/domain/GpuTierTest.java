package com.wrb.devica.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

class GpuTierTest {

    @Test
    void 점수가_가장_낮은_등급만_가장_낮은_등급이다() {
        // given
        GpuTier lowest = Arrays.stream(GpuTier.values())
            .min(Comparator.comparingInt(GpuTier::getMinScore))
            .orElseThrow();

        // when & then
        assertThat(GpuTier.values()).allSatisfy(tier -> assertThat(tier.isLowest()).isEqualTo(tier == lowest));
    }

    @Test
    void 등급끼리_점수가_겹치지_않는다() {
        // when & then
        assertThat(Arrays.stream(GpuTier.values()).map(GpuTier::getMinScore)).doesNotHaveDuplicates();
    }
}
