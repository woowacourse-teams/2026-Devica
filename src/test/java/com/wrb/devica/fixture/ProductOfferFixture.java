package com.wrb.devica.fixture;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.wrb.devica.product.domain.OfferStatus;
import com.wrb.devica.product.domain.Product;
import com.wrb.devica.product.domain.ProductOffer;
import com.wrb.devica.product.domain.Seller;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProductOfferFixture {

    public static ProductOffer onSaleOffer(Product product, long price) {
        return offer().product(product).price(price).build();
    }

    public static ProductOfferBuilder offer() {
        return builder();
    }

    @Builder
    private static ProductOffer offerBuilder(Product product, Seller seller, String name, Long price,
                                             OfferStatus status, LocalDate checkedAt) {
        requireNonNull(product, "오퍼는 제품이 있어야 한다. offer().product(...) 로 지정한다");
        requireNonNull(product.getId(), "오퍼는 저장된 제품의 id 로 잇는다. 제품을 먼저 저장한다");

        return ProductOffer.builder()
            .productId(product.getId())
            // 쿠팡 가격 갱신 대상에 섞이지 않도록 쿠팡이 아닌 판매처를 기본값으로 둔다
            .seller(requireNonNullElse(seller, Seller.STUDENT_STORE))
            .name(requireNonNullElse(name, "판매처"))
            .price(requireNonNullElse(price, 1_000_000L))
            .purchaseUrl("https://example.com/" + product.getCode())
            .status(requireNonNullElse(status, OfferStatus.ON_SALE))
            .checkedAt(checkedAt)
            .build();
    }
}
