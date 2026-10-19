package com.wrb.devica.recommendation.laptop;

import com.wrb.devica.product.domain.CpuTier;
import com.wrb.devica.product.domain.Os;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum LaptopCondition {

    PREFERRED_OS(Os.values()),
    CURRENT_CPU_TIER(CpuTier.values());

    private final Set<String> allowedValues;

    LaptopCondition(Enum<?>[] values) {
        this.allowedValues = Arrays.stream(values).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> allowedValues() {
        return allowedValues;
    }
}
