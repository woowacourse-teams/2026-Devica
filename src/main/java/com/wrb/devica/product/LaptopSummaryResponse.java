package com.wrb.devica.product;

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
    public static LaptopSummaryResponse from(Laptop laptop, long minPrice) {
        return new LaptopSummaryResponse(
            laptop.getId(),
            laptop.getBrand(),
            laptop.getName(),
            minPrice,
            laptop.getOs(),
            laptop.getCpu().getName(),
            laptop.getCpu().getCoreCount(),
            laptop.getMemoryGb(),
            laptop.getStorageGb(),
            laptop.getScreenSizeInch()
        );
    }
}
