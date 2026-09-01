package com.wrb.devica.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "laptop")
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

    @Column(nullable = false)
    private int weightG;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal screenSizeInch;
}
