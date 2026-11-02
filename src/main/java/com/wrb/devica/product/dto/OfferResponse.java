package com.wrb.devica.product.dto;

import com.wrb.devica.product.domain.ProductOffer;
import com.wrb.devica.product.domain.OfferLinkType;
import com.wrb.devica.product.domain.OfferPriceBasis;
import java.time.LocalDate;

public record OfferResponse(
    String name,
    long price,
    String purchaseUrl,
    LocalDate checkedAt,
    Long id,
    String priceCondition,
    OfferPriceBasis priceBasis,
    OfferLinkType linkType
) {
    public static OfferResponse from(ProductOffer offer) {
        return new OfferResponse(
            offer.getName(),
            offer.getPrice(),
            offer.getPurchaseUrl(),
            offer.getCheckedAt(),
            offer.getId(),
            offer.getPriceCondition(),
            offer.getPriceBasis(),
            offer.getLinkType()
        );
    }
}
