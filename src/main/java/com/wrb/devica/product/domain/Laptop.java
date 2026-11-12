package com.wrb.devica.product.domain;

import com.wrb.devica.category.domain.ProductCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ColorGamut colorGamut;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gpu_id", nullable = false)
    private Gpu gpu;

    @Builder
    private Laptop(ProductCategory category, String brand, String name, String code,
                   String description, LocalDate releasedAt, String imageKey,
                   Cpu cpu, Os os, int memoryGb, int storageGb, int weightG,
                   BigDecimal screenSizeInch, ColorGamut colorGamut, Gpu gpu) {
        super(category, brand, name, code, description, releasedAt, imageKey);
        this.cpu = cpu;
        this.os = os;
        this.memoryGb = memoryGb;
        this.storageGb = storageGb;
        this.weightG = weightG;
        this.screenSizeInch = screenSizeInch;
        this.colorGamut = colorGamut;
        this.gpu = gpu;
    }

    public static List<SpecValue> summarySpecValues(Os os, String cpuName, String gpuName, int memoryGb,
                                                    int storageGb, ColorGamut colorGamut) {
        return List.of(
            new SpecValue("OS", os.name()),
            new SpecValue("CPU", cpuName),
            new SpecValue("GPU", gpuName),
            new SpecValue("MEMORY", String.valueOf(memoryGb)),
            new SpecValue("STORAGE", String.valueOf(storageGb)),
            new SpecValue("COLOR_GAMUT", colorGamut.name()));
    }

    public List<SpecValue> allSpecValues() {
        return Stream.concat(
            summarySpecValues(os, cpu.getName(), gpu.getName(), memoryGb, storageGb, colorGamut).stream(),
            Stream.of(
                new SpecValue("CPU_CORE", String.valueOf(cpu.getCoreCount())),
                new SpecValue("SCREEN_SIZE", screenSizeInch.toPlainString()),
                new SpecValue("WEIGHT", String.valueOf(weightG)))
        ).toList();
    }
}
