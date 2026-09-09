package com.wrb.devica.fixture;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.Objects.requireNonNullElseGet;

import com.wrb.devica.category.ProductCategory;
import com.wrb.devica.product.Cpu;
import com.wrb.devica.product.Laptop;
import com.wrb.devica.product.Os;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LaptopFixture {

    @Builder(builderMethodName = "laptop")
    private static Laptop laptopBuilder(ProductCategory category, String brand, String name, String code,
                                        Os os, Cpu cpu, Integer memoryGb, Integer storageGb,
                                        BigDecimal screenSizeInch, Integer weightG) {
        requireNonNull(category, "노트북은 카테고리가 있어야 한다. laptop().category(...) 로 지정한다");
        requireNonNull(cpu, "노트북은 cpu 가 있어야 한다. laptop().cpu(...) 로 지정한다");

        return Laptop.builder()
            .category(category)
            .brand(requireNonNullElse(brand, "브랜드"))
            .name(requireNonNullElse(name, "노트북"))
            .os(requireNonNullElse(os, Os.WINDOWS))
            .memoryGb(requireNonNullElse(memoryGb, 16))
            .storageGb(requireNonNullElse(storageGb, 512))
            .weightG(requireNonNullElse(weightG, 1200))
            .code(requireNonNullElseGet(code, ProductCodeFixture::next))
            .cpu(cpu)
            .screenSizeInch(requireNonNullElse(screenSizeInch, new BigDecimal("16.0")))
            .build();
    }

}
