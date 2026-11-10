package com.wrb.devica.product.domain;

import com.wrb.devica.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
// 같은 제품에 같은 판매처의 같은 판매상품을 두 번 등록하지 않는다. 판매상품 ID가 없는 행(NULL)은 제약에서 제외된다.
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_product_offer_seller_item",
    columnNames = {"product_id", "seller", "external_item_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOffer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Seller seller;

    // 화면에 보이는 판매처 이름. 판매처를 구분할 때는 seller 를 쓴다.
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
    private ProductOffer(Long productId, Seller seller, String name, long price, String externalItemId,
                         String purchaseUrl, OfferStatus status, LocalDate checkedAt) {
        validatePrice(price);
        this.productId = productId;
        this.seller = seller;
        this.name = name;
        this.price = price;
        this.externalItemId = externalItemId;
        this.purchaseUrl = purchaseUrl;
        this.status = status;
        this.checkedAt = checkedAt;
    }

    private void validatePrice(long price) {
        if (price <= 0) {
            throw new IllegalArgumentException("확인하지 않은 가격을 0원 이하로 등록할 수 없습니다.");
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
