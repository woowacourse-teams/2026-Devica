package com.wrb.devica.product;

import jakarta.validation.constraints.Positive;

public record LaptopSearchCondition(
        Os os,
        @Positive(message = "cpu 점수는 1 이상이어야 합니다.")
        Integer cpuScore,
        @Positive(message = "메모리 용량은 1GB 이상이어야 합니다.")
        Integer memoryGb,
        @Positive(message = "스토리지 용량은 1GB 이상이어야 합니다.")
        Integer storageGb
) {
}
