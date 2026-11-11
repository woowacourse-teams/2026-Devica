package com.wrb.devica.product.dto;

import com.wrb.devica.product.domain.ColorGamut;
import com.wrb.devica.product.domain.CpuTier;
import com.wrb.devica.product.domain.GpuTier;
import com.wrb.devica.product.domain.Os;
import com.wrb.devica.product.domain.StorageSize;
import java.util.function.UnaryOperator;

/**
 * 사양 항목의 원본 값에 표시 형태를 입힌다. 예: 24 -> 24GB
 */
public enum SpecItemView {

    OS("운영체제", value -> Os.valueOf(value).getDisplayName()),
    CPU("CPU", UnaryOperator.identity()),
    REQUIRED_CPU("CPU", value -> CpuTier.valueOf(value).getDisplayName()),
    GPU("GPU", UnaryOperator.identity()),
    REQUIRED_GPU("GPU", value -> GpuTier.valueOf(value).getDisplayName()),
    MEMORY("메모리", value -> value + "GB"),
    STORAGE("저장 공간", value -> StorageSize.display(Integer.parseInt(value))),
    CPU_CORE("코어 수", value -> value + "코어"),
    SCREEN_SIZE("화면 크기", value -> value + "인치"),
    WEIGHT("무게", value -> value + "g"),
    COLOR_GAMUT("화면 색 영역", value -> ColorGamut.valueOf(value).getDisplayName());

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
