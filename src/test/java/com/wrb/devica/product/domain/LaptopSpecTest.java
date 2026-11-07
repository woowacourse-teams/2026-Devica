package com.wrb.devica.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LaptopSpecTest {

    @Test
    void 사양을_항목_코드와_원본_값으로_변환한다() {
        // given
        LaptopSpec spec = new LaptopSpec(Os.MAC, CpuTier.BASIC, 24, 512, null, null);

        // when
        List<SpecValue> values = spec.values();

        // then
        assertThat(values)
            .extracting(SpecValue::code, SpecValue::value)
            .containsExactly(
                tuple("OS", "MAC"),
                tuple("REQUIRED_CPU", "BASIC"),
                tuple("MEMORY", "24"),
                tuple("STORAGE", "512"));
    }

    @Test
    void CPU_등급이_OS_와_맞지_않으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> new LaptopSpec(Os.MAC, CpuTier.HX, 24, 512, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"0, 512", "-1, 512", "20, 512", "24, 0", "24, -1", "24, 1000"})
    void 메모리나_저장_공간이_권장_사양에_없는_값이면_예외가_발생한다(int memoryGb, int storageGb) {
        // when & then
        assertThatThrownBy(() -> new LaptopSpec(Os.MAC, CpuTier.BASIC, memoryGb, storageGb, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 화면과_GPU_는_정해졌을_때만_사양_항목에_더한다() {
        // given
        LaptopSpec spec = new LaptopSpec(Os.WINDOWS, CpuTier.H, 32, 1024, ColorGamut.WIDE, GpuTier.XX60);

        // when
        List<SpecValue> values = spec.values();

        // then
        assertThat(values)
            .extracting(SpecValue::code, SpecValue::value)
            .endsWith(tuple("COLOR_GAMUT", "WIDE"), tuple("REQUIRED_GPU", "XX60"));
    }

    @Test
    void GPU_등급이_OS_와_맞지_않으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> new LaptopSpec(Os.MAC, CpuTier.PRO, 24, 512, null, GpuTier.XX50))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
