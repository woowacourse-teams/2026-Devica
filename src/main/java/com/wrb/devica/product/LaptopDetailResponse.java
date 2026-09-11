package com.wrb.devica.product;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LaptopDetailResponse(
        Long id,
        String brand,
        String name,
        String code,
        String description,
        LocalDate releasedAt,
        Os os,
        String cpuName,
        int cpuCoreCount,
        int memoryGb,
        int storageGb,
        int weightG,
        BigDecimal screenSizeInch,
        List<OfferResponse> offers
) {
    public static LaptopDetailResponse of(Laptop laptop, List<ProductOffer> offers) {
        return new LaptopDetailResponse(
            laptop.getId(),
            laptop.getBrand(),
            laptop.getName(),
            laptop.getCode(),
            laptop.getDescription(),
            laptop.getReleasedAt(),
            laptop.getOs(),
            laptop.getCpu().getName(),
            laptop.getCpu().getCoreCount(),
            laptop.getMemoryGb(),
            laptop.getStorageGb(),
            laptop.getWeightG(),
            laptop.getScreenSizeInch(),
            offers.stream().map(OfferResponse::from).toList()
        );
    }
}
