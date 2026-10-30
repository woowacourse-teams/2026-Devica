package com.wrb.devica.product.domain;

import com.wrb.devica.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;
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

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false)
    private long price;

    // 판매처의 판매 옵션 ID. 쿠팡은 purchaseUrl의 vendorItemId를 저장한다(productId, itemId와 구분).
    @Column(name = "external_item_id", length = 64)
    private String externalItemId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String purchaseUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OfferStatus status;

    // 판매처에서 가격을 마지막으로 확인한 날. 가격이 그대로여도 확인하면 갱신하므로 updatedAt 과 다르다
    @Column(name = "checked_at")
    private LocalDate checkedAt;

    @Builder
    private ProductOffer(Long productId, String name, long price, String externalItemId,
                         String purchaseUrl, OfferStatus status, LocalDate checkedAt) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.externalItemId = externalItemId;
        this.purchaseUrl = purchaseUrl;
        this.status = status;
        this.checkedAt = checkedAt;
    }

    public void hide() {
        this.status = OfferStatus.HIDDEN;
    }

    public void refreshPrice(long price, LocalDate checkedAt) {
        if (price <= 0 || checkedAt == null) {
            throw new IllegalArgumentException("확인한 가격은 양수이고 확인 날짜가 있어야 합니다.");
        }
        this.price = price;
        this.checkedAt = checkedAt;
        this.status = OfferStatus.ON_SALE;
    }
}
