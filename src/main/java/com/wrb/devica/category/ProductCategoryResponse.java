package com.wrb.devica.category;

public record ProductCategoryResponse(String code, String name) {

    public static ProductCategoryResponse from(ProductCategoryName productCategoryName) {
        return new ProductCategoryResponse(productCategoryName.name(), productCategoryName.getDisplayName());
    }
}
