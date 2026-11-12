package com.wrb.devica.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GpuTierTest {

    @Test
    void Mac은_M_칩_Windows는_내장_GPU가_가장_낮은_등급이다() {
        // when & then
        assertThat(GpuTier.values())
            .filteredOn(GpuTier::isLowest)
            .containsExactlyInAnyOrder(GpuTier.BASIC, GpuTier.INTEGRATED);
    }

    @ParameterizedTest
    @EnumSource(Os.class)
    void 가장_낮은_등급의_최소_점수는_0이다(Os os) {
        // when & then
        assertThat(GpuTier.values())
            .filteredOn(tier -> tier.getOs() == os && tier.isLowest())
            .singleElement()
            .extracting(GpuTier::getMinScore)
            .isEqualTo(0);
    }

    @ParameterizedTest
    @EnumSource(Os.class)
    void 같은_OS_안에서_점수가_겹치는_등급은_없다(Os os) {
        // given
        List<Integer> scores = Arrays.stream(GpuTier.values())
            .filter(tier -> tier.getOs() == os)
            .map(GpuTier::getMinScore)
            .toList();

        // then
        assertThat(scores).isNotEmpty().doesNotHaveDuplicates();
    }

    @Test
    void 등급은_OS_별로_모아_낮은_등급부터_줄_세운다() {
        // when & then
        assertThat(GpuTier.inOrder()).containsExactly(
            GpuTier.INTEGRATED, GpuTier.XX50, GpuTier.XX60,
            GpuTier.BASIC, GpuTier.PRO, GpuTier.MAX);
    }

    @Test
    void 같은_OS_안에서_점수가_바로_위인_등급으로_올린다() {
        // when & then
        assertThat(GpuTier.INTEGRATED.stepUp()).isEqualTo(GpuTier.XX50);
        assertThat(GpuTier.BASIC.stepUp()).isEqualTo(GpuTier.PRO);
    }

    @ParameterizedTest
    @EnumSource(Os.class)
    void 가장_높은_등급은_더_올라가지_않는다(Os os) {
        // given
        GpuTier highest = Arrays.stream(GpuTier.values())
            .filter(tier -> tier.getOs() == os)
            .max((left, right) -> Integer.compare(left.getMinScore(), right.getMinScore()))
            .orElseThrow();

        // when & then
        assertThat(highest.stepUp()).isEqualTo(highest);
    }

    @Test
    void 점수가_바로_위_등급의_최소_점수_이상이면_조건을_넘는다() {
        // given
        int nextMinScore = GpuTier.XX50.getMinScore();

        // when & then
        assertThat(GpuTier.INTEGRATED.isExceededBy(nextMinScore - 1)).isFalse();
        assertThat(GpuTier.INTEGRATED.isExceededBy(nextMinScore)).isTrue();
    }

    @Test
    void 가장_높은_등급은_점수가_아무리_높아도_넘지_않는다() {
        // when & then
        assertThat(GpuTier.MAX.isExceededBy(Integer.MAX_VALUE)).isFalse();
    }
}
