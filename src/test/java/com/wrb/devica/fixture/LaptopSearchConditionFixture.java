package com.wrb.devica.fixture;

import com.wrb.devica.product.LaptopSearchCondition;
import com.wrb.devica.product.Os;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LaptopSearchConditionFixture {

    public static LaptopSearchConditionBuilder condition() {
        return builder();
    }

    @Builder
    private static LaptopSearchCondition conditionBuilder(Os os, Integer cpuScore, Integer memoryGb,
                                                          Integer storageGb, String keyword, String brand,
                                                          Long minPrice, Long maxPrice) {
        return new LaptopSearchCondition(os, cpuScore, memoryGb, storageGb, keyword, brand, minPrice, maxPrice);
    }
}
