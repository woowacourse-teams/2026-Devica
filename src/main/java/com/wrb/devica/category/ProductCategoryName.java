package com.wrb.devica.category;

import lombok.Getter;

@Getter
public enum ProductCategoryName {

    LAPTOP("노트북");

    private final String displayName;

    ProductCategoryName(String displayName) {
        this.displayName = displayName;
    }
}
