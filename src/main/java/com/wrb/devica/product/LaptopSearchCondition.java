package com.wrb.devica.product;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record LaptopSearchCondition(
        Os os,
        @Positive(message = "cpu 점수는 1 이상이어야 합니다.")
        Integer cpuScore,
        @Positive(message = "메모리 용량은 1GB 이상이어야 합니다.")
        Integer memoryGb,
        @Positive(message = "스토리지 용량은 1GB 이상이어야 합니다.")
        Integer storageGb,
        String keyword,
        String brand,
        @PositiveOrZero(message = "최소 가격은 0 이상이어야 합니다.")
        Long minPrice,
        @PositiveOrZero(message = "최대 가격은 0 이상이어야 합니다.")
        Long maxPrice
) {

    @AssertTrue(message = "최소 가격은 최대 가격보다 클 수 없습니다.")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice <= maxPrice;
    }
}
