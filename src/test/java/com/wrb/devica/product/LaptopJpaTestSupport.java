package com.wrb.devica.product;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.Objects.requireNonNullElseGet;

import com.wrb.devica.category.ProductCategory;
import com.wrb.devica.category.ProductCategoryCode;
import com.wrb.devica.common.JpaSliceTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Builder;
import org.springframework.beans.factory.annotation.Autowired;

@JpaSliceTest
abstract class LaptopJpaTestSupport {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Autowired
    protected EntityManager entityManager;

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Builder(builderMethodName = "category", buildMethodName = "save")
    private ProductCategory categoryBuilder(ProductCategoryCode code) {
        ProductCategoryCode targetCode = requireNonNullElse(code, ProductCategoryCode.LAPTOP);

        return entityManager
            .createQuery("select c from ProductCategory c where c.code = :code", ProductCategory.class)
            .setParameter("code", targetCode)
            .getResultStream()
            .findFirst()
            .orElseGet(() -> {
                ProductCategory created = ProductCategory.from(targetCode);
                entityManager.persist(created);
                return created;
            });
    }

    @Builder(builderMethodName = "cpu", buildMethodName = "save")
    private Cpu cpuBuilder(String manufacturer, String name, Integer coreCount, Integer score) {
        int targetScore = requireNonNullElse(score, 10000);

        Cpu cpu = Cpu.builder()
            .manufacturer(requireNonNullElse(manufacturer, "Intel"))
            .name(requireNonNullElse(name, "Core " + targetScore))
            .coreCount(requireNonNullElse(coreCount, 8))
            .score(targetScore)
            .build();
        entityManager.persist(cpu);
        return cpu;
    }

    @Builder(builderMethodName = "laptop", buildMethodName = "save")
    private Laptop laptopBuilder(ProductCategory category, String brand, String name, String code,
                                 Os os, Cpu cpu, Integer cpuScore, Integer memoryGb, Integer storageGb,
                                 BigDecimal screenSizeInch, Integer weightG) {
        Laptop laptop = Laptop.builder()
            .brand(requireNonNullElse(brand, "브랜드"))
            .name(requireNonNullElse(name, "노트북"))
            .os(requireNonNullElse(os, Os.WINDOWS))
            .memoryGb(requireNonNullElse(memoryGb, 16))
            .storageGb(requireNonNullElse(storageGb, 512))
            .weightG(requireNonNullElse(weightG, 1200))
            .code(requireNonNullElse(code, "CODE-" + SEQUENCE.incrementAndGet()))
            .category(requireNonNullElseGet(category, () -> categoryBuilder(null)))
            .cpu(requireNonNullElseGet(cpu, () -> cpuBuilder(null, null, null, cpuScore)))
            .screenSizeInch(requireNonNullElse(screenSizeInch, new BigDecimal("16.0")))
            .build();
        entityManager.persist(laptop);

        return laptop;
    }

    @Builder(builderMethodName = "offer", buildMethodName = "save")
    private ProductOffer offerBuilder(Product product, String name,
                                      Long price, OfferStatus status) {
        requireNonNull(product, "오퍼를 저장하려면 제품이 있어야 한다. offer().product(...) 로 지정한다");

        ProductOffer offer = ProductOffer.builder()
            .product(product)
            .name(requireNonNullElse(name, "판매처"))
            .price(requireNonNullElse(price, 1_000_000L))
            .purchaseUrl("https://example.com/" + product.getName())
            .status(requireNonNullElse(status, OfferStatus.ON_SALE))
            .build();
        entityManager.persist(offer);
        return offer;
    }

}
