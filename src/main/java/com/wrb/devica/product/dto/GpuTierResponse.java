package com.wrb.devica.product.dto;

import com.wrb.devica.product.domain.GpuTier;

public record GpuTierResponse(String code, String name, String os, boolean lowest) {

    public static GpuTierResponse from(GpuTier tier) {
        return new GpuTierResponse(tier.name(), tier.getDisplayName(), tier.getOs().name(), tier.isLowest());
    }
}
