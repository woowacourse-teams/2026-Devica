package com.wrb.devica.product;

import com.wrb.devica.category.ProductCategory;
import com.wrb.devica.category.ProductCategoryName;
import com.wrb.devica.common.JpaSliceTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

@JpaSliceTest
abstract class LaptopJpaTestSupport {

    private static final long DEFAULT_PRICE = 1_000_000L;

    @Autowired
    protected EntityManager entityManager;

    protected ProductCategory category;

    @BeforeEach
    void setUpCategory() {
        category = ProductCategory.from(ProductCategoryName.LAPTOP);
        entityManager.persist(category);
    }

    protected Laptop saveOnSaleLaptop(String name, Os os, int cpuScore, int memoryGb, int storageGb) {
        Laptop laptop = saveLaptopWithoutOffer(name, os, cpuScore, memoryGb, storageGb);
        saveOffer(laptop, DEFAULT_PRICE, OfferStatus.ON_SALE);
        return laptop;
    }

    protected Laptop saveLaptopWithoutOffer(String name, Os os, int cpuScore, int memoryGb, int storageGb) {
        Cpu cpu = Cpu.builder()
            .manufacturer("Intel")
            .name("Core " + cpuScore)
            .coreCount(8)
            .score(cpuScore)
            .build();
        entityManager.persist(cpu);

        Laptop laptop = Laptop.builder()
            .category(category)
            .brand("브랜드")
            .name(name)
            .code("CODE-" + name)
            .cpu(cpu)
            .os(os)
            .memoryGb(memoryGb)
            .storageGb(storageGb)
            .weightG(1200)
            .screenSizeInch(new BigDecimal("16.0"))
            .build();
        entityManager.persist(laptop);
        return laptop;
    }

    protected void saveOffer(Laptop laptop, long price, OfferStatus status) {
        entityManager.persist(ProductOffer.builder()
            .product(laptop)
            .name("판매처")
            .price(price)
            .purchaseUrl("https://example.com/" + laptop.getName())
            .status(status)
            .build());
    }

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
