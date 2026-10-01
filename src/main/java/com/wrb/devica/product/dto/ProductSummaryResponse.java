package com.wrb.devica.product.dto;

import java.util.List;

public record ProductSummaryResponse(
    Long id,
    String brand,
    String name,
    String imageUrl,
    Long minPrice,
    List<SpecItemResponse> specs
) {
    public static ProductSummaryResponse from(ProductSummaryRow row, String imageUrl) {
        return new ProductSummaryResponse(row.id(), row.brand(), row.name(), imageUrl, row.minPrice(), row.specs());
    }
}
