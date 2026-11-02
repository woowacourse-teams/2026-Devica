package com.wrb.devica.product.dto;

import com.wrb.devica.product.domain.ProductOffer;
import java.util.List;

public record ProductSummaryResponse(
    Long id,
    String brand,
    String name,
    String imageUrl,
    Long minPrice,
    List<SpecItemResponse> specs,
    OfferResponse minPriceOffer
) {
    public static ProductSummaryResponse from(ProductSummaryRow row, String imageUrl, ProductOffer minPriceOffer) {
        return new ProductSummaryResponse(row.id(), row.brand(), row.name(), imageUrl, row.minPrice(), row.specs(),
            minPriceOffer == null ? null : OfferResponse.from(minPriceOffer));
    }
}
