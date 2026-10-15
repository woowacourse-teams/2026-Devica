package com.wrb.devica.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMinPrice {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "min_price", nullable = false)
    private long minPrice;
}
