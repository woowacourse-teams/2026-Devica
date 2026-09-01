package com.wrb.devica.category;

public record ProductCategoryResponse(String code, String name) {

    public static ProductCategoryResponse from(ProductCategoryCode productCategoryCode) {
        return new ProductCategoryResponse(productCategoryCode.name(), productCategoryCode.getDisplayName());
    }
}
