package com.wrb.devica.product;

import com.querydsl.core.annotations.QueryProjection;
import java.util.List;

public record ProductSummaryResponse(
    Long id,
    String brand,
    String name,
    Long minPrice,
    List<SpecItemResponse> specs
) {

    @QueryProjection
    public ProductSummaryResponse(Long id, String brand, String name, Long minPrice,
                                  Os os, String cpuName, int memoryGb, int storageGb) {
        this(id, brand, name, minPrice,
            SpecItemResponse.from(Laptop.summarySpecValues(os, cpuName, memoryGb, storageGb)));
    }
}
