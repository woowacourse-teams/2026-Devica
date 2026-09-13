package com.wrb.devica.product;

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
        LaptopSpec spec = new LaptopSpec(Os.MAC, CpuTier.BASIC, 24, 512);

        // when
        List<SpecValue> values = spec.values();

        // then
        assertThat(values)
            .extracting(SpecValue::code, SpecValue::value)
            .containsExactly(
                tuple("OS", "MAC"),
                tuple("CPU_TIER", "BASIC"),
                tuple("MEMORY", "24"),
                tuple("STORAGE", "512"));
    }

    @Test
    void CPU_등급이_OS_와_맞지_않으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> new LaptopSpec(Os.MAC, CpuTier.HX, 24, 512))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"0, 512", "-1, 512", "24, 0", "24, -1"})
    void 메모리나_저장_공간이_0_이하면_예외가_발생한다(int memoryGb, int storageGb) {
        // when & then
        assertThatThrownBy(() -> new LaptopSpec(Os.MAC, CpuTier.BASIC, memoryGb, storageGb))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
