package com.wrb.devica.product.domain;

import java.util.Arrays;
import lombok.Getter;

@Getter
public enum GpuTier {

    ENTRY("기본 GPU", 0),
    MID("중급 GPU (RTX 4050 급)", 6000),
    HIGH("고성능 GPU (RTX 4060 급 이상)", 9500);

    private final String displayName;
    private final int minScore;

    GpuTier(String displayName, int minScore) {
        this.displayName = displayName;
        this.minScore = minScore;
    }

    public boolean isLowest() {
        return Arrays.stream(values()).noneMatch(tier -> tier.minScore < minScore);
    }
}
