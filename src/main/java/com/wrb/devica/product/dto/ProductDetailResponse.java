package com.wrb.devica.product.dto;

import com.wrb.devica.product.domain.Product;
import com.wrb.devica.product.domain.ProductOffer;
import java.time.LocalDate;
import java.util.List;

public record ProductDetailResponse(
    Long id,
    String brand,
    String name,
    String code,
    String description,
    LocalDate releasedAt,
    List<SpecItemResponse> specs,
    List<OfferResponse> offers
) {
    public static ProductDetailResponse of(Product product, List<ProductOffer> offers) {
        return new ProductDetailResponse(
            product.getId(),
            product.getBrand(),
            product.getName(),
            product.getCode(),
            product.getDescription(),
            product.getReleasedAt(),
            SpecItemResponse.from(product.allSpecValues()),
            offers.stream().map(OfferResponse::from).toList()
        );
    }
}
