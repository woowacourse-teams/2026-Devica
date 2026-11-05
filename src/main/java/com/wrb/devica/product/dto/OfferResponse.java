package com.wrb.devica.product.dto;

import com.wrb.devica.product.domain.OfferStatus;
import com.wrb.devica.product.domain.ProductOffer;
import java.time.LocalDate;

public record OfferResponse(
    String name,
    long price,
    String purchaseUrl,
    LocalDate checkedAt,
    Long id,
    OfferStatus status
) {
    public static OfferResponse from(ProductOffer offer) {
        return new OfferResponse(
            offer.getName(),
            offer.getPrice(),
            offer.getPurchaseUrl(),
            offer.getCheckedAt(),
            offer.getId(),
            offer.getStatus()
        );
    }
}
