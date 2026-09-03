package com.wrb.devica.product;

import com.querydsl.core.annotations.QueryProjection;
import java.math.BigDecimal;

public record LaptopSummaryResponse(
        Long id,
        String brand,
        String name,
        long minPrice,
        Os os,
        String cpuName,
        int cpuCoreCount,
        int memoryGb,
        int storageGb,
        BigDecimal screenSizeInch
) {

    @QueryProjection
    public LaptopSummaryResponse {
    }
}
