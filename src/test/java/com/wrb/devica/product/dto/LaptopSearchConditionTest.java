package com.wrb.devica.product.dto;

import static com.wrb.devica.fixture.LaptopSearchConditionFixture.condition;
import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.product.domain.GpuTier;
import com.wrb.devica.product.domain.Os;
import org.junit.jupiter.api.Test;

class LaptopSearchConditionTest {

    @Test
    void OS와_맞지_않는_GPU_등급은_유효하지_않다() {
        // given
        LaptopSearchCondition condition = condition().os(Os.MAC).gpuTier(GpuTier.XX60).build();

        // when & then
        assertThat(condition.isGpuTierOfOs()).isFalse();
    }

    @Test
    void OS와_맞는_GPU_등급은_유효하다() {
        // given
        LaptopSearchCondition condition = condition().os(Os.MAC).gpuTier(GpuTier.PRO).build();

        // when & then
        assertThat(condition.isGpuTierOfOs()).isTrue();
    }
}
