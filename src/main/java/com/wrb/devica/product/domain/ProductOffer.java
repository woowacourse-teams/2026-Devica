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
import java.util.Objects;
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

    @Column(nullable = false, length = 255)
    private String priceCondition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OfferPriceBasis priceBasis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OfferLinkType linkType;

    @Builder
    private ProductOffer(Long productId, String name, long price, String externalItemId,
                         String purchaseUrl, OfferStatus status, LocalDate checkedAt,
                         String priceCondition, OfferPriceBasis priceBasis, OfferLinkType linkType) {
        validatePrice(price);
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.externalItemId = externalItemId;
        this.purchaseUrl = purchaseUrl;
        this.status = status;
        this.checkedAt = checkedAt;
        initializePriceContext(priceCondition, priceBasis, linkType);
    }

    private void validatePrice(long price) {
        if (price <= 0) {
            throw new IllegalArgumentException("확인하지 않은 가격을 0원 이하로 등록할 수 없습니다.");
        }
    }

    private void initializePriceContext(String priceCondition, OfferPriceBasis priceBasis, OfferLinkType linkType) {
        this.priceBasis = Objects.requireNonNullElse(priceBasis, OfferPriceBasis.SELLER_PRICE);
        this.linkType = Objects.requireNonNullElse(linkType, OfferLinkType.PURCHASE);
        this.priceCondition = normalizePriceCondition(priceCondition);
        validateObservedPrice(priceCondition);
        validateSearchLink();
    }

    private String normalizePriceCondition(String priceCondition) {
        String normalized = Objects.requireNonNullElse(priceCondition, "일반 판매가").strip();
        if (normalized.isBlank() || normalized.length() > 255) {
            throw new IllegalArgumentException("가격 조건은 1~255자로 입력해야 합니다.");
        }
        return normalized;
    }

    private void validateObservedPrice(String priceCondition) {
        if (priceBasis == OfferPriceBasis.OBSERVED_LOWEST && (priceCondition == null || checkedAt == null)) {
            throw new IllegalArgumentException("관측 최저가는 관측 범위와 확인 날짜가 있어야 합니다.");
        }
    }

    private void validateSearchLink() {
        if (linkType == OfferLinkType.SEARCH && (priceBasis != OfferPriceBasis.OBSERVED_LOWEST || externalItemId != null)) {
            throw new IllegalArgumentException("검색 링크는 개별 판매 옵션 ID가 없는 관측 최저가에 사용합니다.");
        }
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
