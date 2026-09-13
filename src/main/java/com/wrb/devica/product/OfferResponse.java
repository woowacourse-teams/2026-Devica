package com.wrb.devica.product;

public record OfferResponse(
        String name,
        long price,
        String purchaseUrl
) {
    public static OfferResponse from(ProductOffer offer) {
        return new OfferResponse(
            offer.getName(),
            offer.getPrice(),
            offer.getPurchaseUrl()
        );
    }
}
