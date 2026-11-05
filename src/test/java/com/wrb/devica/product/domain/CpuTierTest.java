package com.wrb.devica.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class CpuTierTest {

    @Test
    void 같은_OS_안에서_점수가_바로_위인_등급으로_올린다() {
        // when & then
        assertThat(CpuTier.U.stepUp()).isEqualTo(CpuTier.P_HS);
        assertThat(CpuTier.P_HS.stepUp()).isEqualTo(CpuTier.H);
        assertThat(CpuTier.BASIC.stepUp()).isEqualTo(CpuTier.PRO);
    }

    @ParameterizedTest
    @EnumSource(Os.class)
    void 가장_높은_등급은_더_올라가지_않는다(Os os) {
        // given
        CpuTier highest = Arrays.stream(CpuTier.values())
            .filter(tier -> tier.getOs() == os)
            .max((left, right) -> Integer.compare(left.getMinScore(), right.getMinScore()))
            .orElseThrow();

        // when & then
        assertThat(highest.stepUp()).isEqualTo(highest);
    }

    @ParameterizedTest
    @EnumSource(Os.class)
    void 같은_OS_안에서_점수가_겹치는_등급은_없다(Os os) {
        // given
        List<Integer> scores = Arrays.stream(CpuTier.values())
            .filter(tier -> tier.getOs() == os)
            .map(CpuTier::getMinScore)
            .toList();

        // then
        assertThat(scores).isNotEmpty().doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @ValueSource(ints = {48861, 57153})
    void M5_Pro_칩_점수는_M_Max_칩_등급에_들지_않는다(int m5ProScore) {
        // when & then
        assertThat(m5ProScore).isGreaterThanOrEqualTo(CpuTier.PRO.getMinScore())
            .isLessThan(CpuTier.MAX.getMinScore());
    }

    @Test
    void 등급은_OS_별로_모아_낮은_등급부터_줄_세운다() {
        // when & then
        assertThat(CpuTier.inOrder()).containsExactly(
            CpuTier.U, CpuTier.P_HS, CpuTier.H, CpuTier.HX,
            CpuTier.BASIC, CpuTier.PRO, CpuTier.MAX);
    }

    @Test
    void 등급_비교는_점수로_한다() {
        // when & then
        assertThat(CpuTier.H.higherOf(CpuTier.P_HS)).isEqualTo(CpuTier.H);
        assertThat(CpuTier.U.higherOf(CpuTier.HX)).isEqualTo(CpuTier.HX);
    }

    @Test
    void 점수가_더_높아야_높은_등급이다() {
        // when & then
        assertThat(CpuTier.H.isHigherThan(CpuTier.P_HS)).isTrue();
        assertThat(CpuTier.P_HS.isHigherThan(CpuTier.H)).isFalse();
        assertThat(CpuTier.H.isHigherThan(CpuTier.H)).isFalse();
    }
}
