package com.wrb.devica.product.dto;

import com.wrb.devica.product.domain.ColorGamut;
import com.wrb.devica.product.domain.CpuTier;
import com.wrb.devica.product.domain.GpuTier;
import com.wrb.devica.product.domain.Os;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record LaptopSearchCondition(
    Os os,
    CpuTier cpuTier,
    ColorGamut colorGamut,
    GpuTier gpuTier,
    @Positive(message = "메모리 용량은 1GB 이상이어야 합니다.")
    Integer memoryGb,
    @Positive(message = "스토리지 용량은 1GB 이상이어야 합니다.")
    Integer storageGb,
    String keyword,
    @Size(max = 64, message = "브랜드는 64자 이하여야 합니다.")
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

    @AssertTrue(message = "CPU 등급이 OS 와 맞지 않습니다.")
    public boolean isCpuTierOfOs() {
        return os == null || cpuTier == null || cpuTier.getOs() == os;
    }

    @AssertTrue(message = "GPU 등급이 OS 와 맞지 않습니다.")
    public boolean isGpuTierOfOs() {
        return os == null || gpuTier == null || gpuTier.getOs() == os;
    }
}
