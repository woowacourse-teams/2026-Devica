package com.wrb.devica.product;

import com.wrb.devica.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cpu extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String manufacturer;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private int coreCount;

    @Column(nullable = false)
    private int score;

    @Builder
    private Cpu(String manufacturer, String name, int coreCount, int score) {
        this.manufacturer = manufacturer;
        this.name = name;
        this.coreCount = coreCount;
        this.score = score;
    }
}
