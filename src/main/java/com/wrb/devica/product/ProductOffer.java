package com.wrb.devica.product;

import com.wrb.devica.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOffer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false)
    private long price;

    @Column(name = "external_item_id", length = 64)
    private String externalItemId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String purchaseUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OfferStatus status;

    @Builder
    private ProductOffer(Product product, String name, long price, String externalItemId,
                         String purchaseUrl, OfferStatus status) {
        this.product = product;
        this.name = name;
        this.price = price;
        this.externalItemId = externalItemId;
        this.purchaseUrl = purchaseUrl;
        this.status = status;
    }
}
