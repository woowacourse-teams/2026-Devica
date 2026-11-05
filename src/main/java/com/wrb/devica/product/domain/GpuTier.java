package com.wrb.devica.product.domain;

import java.util.Arrays;
import lombok.Getter;

@Getter
public enum GpuTier {

    BASIC(Os.MAC, "M 칩", 0),
    PRO(Os.MAC, "M Pro 칩", 8000),
    MAX(Os.MAC, "M Max 칩", 12000),

    INTEGRATED(Os.WINDOWS, "내장 GPU", 0),
    XX50(Os.WINDOWS, "RTX xx50 급", 6000),
    XX60(Os.WINDOWS, "RTX xx60 급", 9500);

    private final Os os;
    private final String displayName;
    private final int minScore;

    GpuTier(Os os, String displayName, int minScore) {
        this.os = os;
        this.displayName = displayName;
        this.minScore = minScore;
    }

    public boolean isLowest() {
        return Arrays.stream(values()).noneMatch(tier -> tier.os == os && tier.minScore < minScore);
    }
}
