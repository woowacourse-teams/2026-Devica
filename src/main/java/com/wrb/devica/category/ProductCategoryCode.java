package com.wrb.devica.category;

import lombok.Getter;

@Getter
public enum ProductCategoryCode {

    LAPTOP("노트북");

    private final String displayName;

    ProductCategoryCode(String displayName) {
        this.displayName = displayName;
    }
}
