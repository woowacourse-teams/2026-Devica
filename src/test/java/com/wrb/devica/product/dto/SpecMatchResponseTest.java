package com.wrb.devica.product.dto;

import static com.wrb.devica.fixture.LaptopSearchConditionFixture.condition;
import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.product.domain.ColorGamut;
import com.wrb.devica.product.domain.CpuTier;
import com.wrb.devica.product.domain.GpuTier;
import com.wrb.devica.product.domain.Os;
import com.wrb.devica.product.dto.ProductSummaryRow.MatchBasis;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpecMatchResponseTest {

    private static final MatchBasis BASIS = basis(CpuTier.PRO.getMinScore(), GpuTier.PRO.getMinScore(), 24, 512,
        ColorGamut.STANDARD);

    @Test
    void 사양_조건이_없으면_판정하지_않는다() {
        // given
        LaptopSearchCondition condition = condition().os(Os.MAC).keyword("맥북").brand("Apple").maxPrice(3_000_000L)
            .build();

        // when & then
        assertThat(SpecMatchResponse.of(condition, BASIS)).isNull();
    }

    @Test
    void 모든_항목이_조건과_같은_단계면_일치한다() {
        // given
        LaptopSearchCondition condition = condition().os(Os.MAC).cpuTier(CpuTier.PRO).gpuTier(GpuTier.PRO)
            .memoryGb(24).storageGb(512).colorGamut(ColorGamut.STANDARD).build();

        // when
        SpecMatchResponse specMatch = SpecMatchResponse.of(condition, BASIS);

        // then
        assertThat(specMatch).isEqualTo(new SpecMatchResponse(SpecMatchStatus.EXACT, List.of()));
    }

    @Test
    void 조건보다_높은_항목을_사양_순서대로_모은다() {
        // given
        LaptopSearchCondition condition = condition().os(Os.MAC).cpuTier(CpuTier.BASIC).gpuTier(GpuTier.BASIC)
            .memoryGb(16).storageGb(256).build();

        // when
        SpecMatchResponse specMatch = SpecMatchResponse.of(condition, BASIS);

        // then
        assertThat(specMatch).isEqualTo(new SpecMatchResponse(SpecMatchStatus.EXCEEDED,
            List.of(SpecItemView.CPU, SpecItemView.GPU, SpecItemView.MEMORY, SpecItemView.STORAGE)));
    }

    @Test
    void 가장_낮은_단계의_조건도_비교한다() {
        // given
        MatchBasis wide = basis(CpuTier.PRO.getMinScore(), GpuTier.PRO.getMinScore(), 24, 512, ColorGamut.WIDE);
        LaptopSearchCondition condition = condition().os(Os.MAC).gpuTier(GpuTier.BASIC)
            .colorGamut(ColorGamut.STANDARD).build();

        // when
        SpecMatchResponse specMatch = SpecMatchResponse.of(condition, wide);

        // then
        assertThat(specMatch.exceededSpecs()).containsExactly(SpecItemView.GPU, SpecItemView.COLOR_GAMUT);
    }

    @Test
    void 조건에_없는_항목은_비교하지_않는다() {
        // given
        LaptopSearchCondition condition = condition().memoryGb(24).build();

        // when
        SpecMatchResponse specMatch = SpecMatchResponse.of(condition, BASIS);

        // then
        assertThat(specMatch.status()).isEqualTo(SpecMatchStatus.EXACT);
    }

    private static MatchBasis basis(int cpuScore, int gpuScore, int memoryGb, int storageGb,
                                    ColorGamut colorGamut) {
        return new MatchBasis(cpuScore, gpuScore, memoryGb, storageGb, colorGamut);
    }
}
