package com.wrb.devica.product;

import java.util.function.UnaryOperator;

/**
 * 사양 항목의 원본 값에 표시 형태를 입힌다. 예: 24 -> 24GB
 */
public enum SpecItemView {

    OS("운영체제", value -> Os.valueOf(value).getDisplayName()),
    CPU("CPU", UnaryOperator.identity()),
    CPU_TIER("CPU", value -> CpuTier.valueOf(value).getDisplayName()),
    MEMORY("메모리", value -> value + "GB"),
    STORAGE("저장 공간", value -> value + "GB"),
    CPU_CORE("코어 수", value -> value + "코어"),
    SCREEN_SIZE("화면 크기", value -> value + "인치"),
    WEIGHT("무게", value -> value + "g");

    private final String displayName;
    private final UnaryOperator<String> displayValue;

    SpecItemView(String displayName, UnaryOperator<String> displayValue) {
        this.displayName = displayName;
        this.displayValue = displayValue;
    }

    public String displayName() {
        return displayName;
    }

    public String displayValue(String value) {
        return displayValue.apply(value);
    }
}
