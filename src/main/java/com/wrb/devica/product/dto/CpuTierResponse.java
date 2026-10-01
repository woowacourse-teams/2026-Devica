package com.wrb.devica.product.dto;

import com.wrb.devica.product.domain.CpuTier;

public record CpuTierResponse(String code, String name, String os) {

    public static CpuTierResponse from(CpuTier tier) {
        return new CpuTierResponse(tier.name(), tier.getDisplayName(), tier.getOs().name());
    }
}
