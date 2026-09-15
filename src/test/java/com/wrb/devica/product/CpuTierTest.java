package com.wrb.devica.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.question.OptionCode;
import com.wrb.devica.question.option.CurrentMacCpu;
import com.wrb.devica.question.option.CurrentWindowsCpu;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
        assertThat(scores).doesNotHaveDuplicates();
    }

    @Test
    void 등급_비교는_점수로_한다() {
        // when & then
        assertThat(CpuTier.H.higherOf(CpuTier.P_HS)).isEqualTo(CpuTier.H);
        assertThat(CpuTier.U.higherOf(CpuTier.HX)).isEqualTo(CpuTier.HX);
    }

    // 사용자가 답변으로 고르는 현재 CPU 를 알고리즘이 이름으로 등급에 잇는다
    @Test
    void 현재_CPU_선택지_이름마다_같은_이름의_등급이_있다() {
        // when & then
        assertThat(Stream.of(CurrentMacCpu.values(), CurrentWindowsCpu.values())
            .flatMap(Stream::of)
            .map(OptionCode::name))
            .allSatisfy(name -> assertThat(CpuTier.valueOf(name)).isNotNull());
    }
}
