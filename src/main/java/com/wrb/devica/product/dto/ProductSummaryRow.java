package com.wrb.devica.product.dto;

import com.querydsl.core.annotations.QueryProjection;
import com.wrb.devica.product.domain.Laptop;
import com.wrb.devica.product.domain.Os;
import java.util.List;

public record ProductSummaryRow(
    Long id,
    String brand,
    String name,
    String imageKey,
    Long minPrice,
    List<SpecItemResponse> specs
) {

    @QueryProjection
    public ProductSummaryRow(Long id, String brand, String name, String imageKey, Long minPrice,
                             Os os, String cpuName, int memoryGb, int storageGb) {
        this(id, brand, name, imageKey, minPrice,
            SpecItemResponse.from(Laptop.summarySpecValues(os, cpuName, memoryGb, storageGb)));
    }
}
