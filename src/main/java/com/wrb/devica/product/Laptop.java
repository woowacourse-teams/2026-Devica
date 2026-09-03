package com.wrb.devica.product;

import com.wrb.devica.category.ProductCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Laptop extends Product {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cpu_id", nullable = false)
    private Cpu cpu;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Os os;

    @Column(nullable = false)
    private int memoryGb;

    @Column(nullable = false)
    private int storageGb;

    @Column(name = "weight_g", nullable = false)
    private int weightG;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal screenSizeInch;

    @Builder
    private Laptop(ProductCategory category, String brand, String name, String code,
                   String description, LocalDate releasedAt,
                   Cpu cpu, Os os, int memoryGb, int storageGb, int weightG,
                   BigDecimal screenSizeInch) {
        super(category, brand, name, code, description, releasedAt);
        this.cpu = cpu;
        this.os = os;
        this.memoryGb = memoryGb;
        this.storageGb = storageGb;
        this.weightG = weightG;
        this.screenSizeInch = screenSizeInch;
    }
}
