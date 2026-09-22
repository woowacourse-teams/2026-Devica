package com.wrb.devica.fixture;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.wrb.devica.product.domain.OfferStatus;
import com.wrb.devica.product.domain.Product;
import com.wrb.devica.product.domain.ProductOffer;
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
    private static ProductOffer offerBuilder(Product product, String name, Long price, OfferStatus status) {
        requireNonNull(product, "오퍼는 제품이 있어야 한다. offer().product(...) 로 지정한다");
        requireNonNull(product.getId(), "오퍼는 저장된 제품의 id 로 잇는다. 제품을 먼저 저장한다");

        return ProductOffer.builder()
            .productId(product.getId())
            .name(requireNonNullElse(name, "판매처"))
            .price(requireNonNullElse(price, 1_000_000L))
            .purchaseUrl("https://example.com/" + product.getCode())
            .status(requireNonNullElse(status, OfferStatus.ON_SALE))
            .build();
    }
}
