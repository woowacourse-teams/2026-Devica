package com.wrb.devica.category.dto;

import com.wrb.devica.category.domain.ProductCategoryCode;

public record ProductCategoryResponse(String code, String name) {

    public static ProductCategoryResponse from(ProductCategoryCode productCategoryCode) {
        return new ProductCategoryResponse(productCategoryCode.name(), productCategoryCode.getDisplayName());
    }
}
