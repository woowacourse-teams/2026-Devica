package com.wrb.devica.product.domain;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;

@Getter
public enum ColorGamut {

    STANDARD("표준 색 영역(sRGB)"),
    WIDE("넓은 색 영역(DCI-P3)");

    private final String displayName;

    ColorGamut(String displayName) {
        this.displayName = displayName;
    }

    public List<ColorGamut> andAbove() {
        return Arrays.stream(values()).filter(gamut -> gamut.compareTo(this) >= 0).toList();
    }
}
