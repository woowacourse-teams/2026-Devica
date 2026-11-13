package com.wrb.devica.product.dto;

import com.querydsl.core.annotations.QueryProjection;
import com.wrb.devica.product.domain.ColorGamut;
import com.wrb.devica.product.domain.Laptop;
import com.wrb.devica.product.domain.Os;
import java.util.List;

public record ProductSummaryRow(
    Long id,
    String brand,
    String name,
    String imageKey,
    Long minPrice,
    List<SpecItemResponse> specs,
    MatchBasis matchBasis
) {

    @QueryProjection
    public ProductSummaryRow(Long id, String brand, String name, String imageKey, Long minPrice, Os os, String cpuName,
        int cpuScore, String gpuName, int gpuScore, int memoryGb, int storageGb, ColorGamut colorGamut) {
        this(id, brand, name, imageKey, minPrice,
            SpecItemResponse.from(Laptop.summarySpecValues(os, cpuName, gpuName, memoryGb, storageGb, colorGamut)),
            new MatchBasis(cpuScore, gpuScore, memoryGb, storageGb, colorGamut));
    }

    public record MatchBasis(int cpuScore, int gpuScore, int memoryGb, int storageGb, ColorGamut colorGamut) {
    }
}
