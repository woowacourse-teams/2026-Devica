package com.wrb.devica.recommendation;

import com.wrb.devica.product.Os;
import java.util.function.UnaryOperator;

/**
 * 사양 항목의 원본 값에 표시 형태를 입힌다. 예: 24 -> 24GB
 */
enum SpecItemView {

    OS("운영체제", value -> Os.valueOf(value).getDisplayName()),
    CPU("CPU", UnaryOperator.identity()),
    MEMORY("메모리", value -> value + "GB"),
    STORAGE("저장 공간", value -> value + "GB");

    private final String displayName;
    private final UnaryOperator<String> displayValue;

    SpecItemView(String displayName, UnaryOperator<String> displayValue) {
        this.displayName = displayName;
        this.displayValue = displayValue;
    }

    String displayName() {
        return displayName;
    }

    String displayValue(String value) {
        return displayValue.apply(value);
    }
}
