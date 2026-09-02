package com.wrb.devica.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LaptopSpecTest {

    @Test
    void 사양을_항목_코드와_표시값으로_변환한다() {
        // given
        LaptopSpec spec = new LaptopSpec(Os.MACOS, "M 칩", 24, 512);

        // when
        List<SpecItem> items = spec.toItems();

        // then
        assertThat(items)
            .extracting(SpecItem::code, SpecItem::value, SpecItem::displayValue)
            .containsExactly(
                tuple("OS", "MACOS", "Mac"),
                tuple("CPU", "M 칩", "M 칩"),
                tuple("MEMORY", "24", "24GB"),
                tuple("STORAGE", "512", "512GB"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void CPU_가_비어_있으면_예외가_발생한다(String cpu) {
        // when & then
        assertThatThrownBy(() -> new LaptopSpec(Os.MACOS, cpu, 24, 512))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"0, 512", "-1, 512", "24, 0", "24, -1"})
    void 메모리나_저장_공간이_0_이하면_예외가_발생한다(int memoryGb, int storageGb) {
        // when & then
        assertThatThrownBy(() -> new LaptopSpec(Os.MACOS, "M 칩", memoryGb, storageGb))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
