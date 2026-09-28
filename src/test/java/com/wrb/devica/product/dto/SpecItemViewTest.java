package com.wrb.devica.product.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpecItemViewTest {

    @ParameterizedTest
    @CsvSource({"512, 512GB", "1024, 1TB", "1536, 1536GB"})
    void 저장_공간은_TB_로_나누어떨어질_때만_TB_로_표시한다(String value, String expected) {
        // when & then
        assertThat(SpecItemView.STORAGE.displayValue(value)).isEqualTo(expected);
    }
}
