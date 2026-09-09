package com.wrb.devica.fixture;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.wrb.devica.product.OfferStatus;
import com.wrb.devica.product.Product;
import com.wrb.devica.product.ProductOffer;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProductOfferFixture {

    public static ProductOffer 판매_중_오퍼(Product product) {
        return offer().product(product).build();
    }

    public static ProductOffer 판매_중_오퍼(Product product, long price) {
        return offer().product(product).price(price).build();
    }

    @Builder(builderMethodName = "offer")
    private static ProductOffer offerBuilder(Product product, String name, Long price, OfferStatus status) {
        requireNonNull(product, "오퍼는 제품이 있어야 한다. offer().product(...) 로 지정한다");

        return ProductOffer.builder()
            .product(product)
            .name(requireNonNullElse(name, "판매처"))
            .price(requireNonNullElse(price, 1_000_000L))
            .purchaseUrl("https://example.com/" + product.getCode())
            .status(requireNonNullElse(status, OfferStatus.ON_SALE))
            .build();
    }
}
