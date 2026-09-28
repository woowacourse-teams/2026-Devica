package com.wrb.devica.fixture;

import com.wrb.devica.product.domain.CpuTier;
import com.wrb.devica.product.domain.Os;
import com.wrb.devica.product.dto.LaptopSearchCondition;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LaptopSearchConditionFixture {

    public static LaptopSearchConditionBuilder condition() {
        return builder();
    }

    @Builder
    private static LaptopSearchCondition conditionBuilder(Os os, CpuTier cpuTier, Integer memoryGb,
                                                          Integer storageGb, String keyword, String brand,
                                                          Long minPrice, Long maxPrice) {
        return new LaptopSearchCondition(os, cpuTier, memoryGb, storageGb, keyword, brand, minPrice, maxPrice);
    }
}
