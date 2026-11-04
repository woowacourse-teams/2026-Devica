package com.wrb.devica.product.domain;

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
public class Gpu extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String manufacturer;

    @Column(nullable = false, length = 128)
    private String name;

    private Integer vramGb;

    @Column(nullable = false)
    private int score;

    @Builder
    private Gpu(String manufacturer, String name, Integer vramGb, int score) {
        this.manufacturer = manufacturer;
        this.name = name;
        this.vramGb = vramGb;
        this.score = score;
    }
}
