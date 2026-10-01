package com.wrb.devica.category.domain;

import com.wrb.devica.common.exception.BusinessErrorCode;
import com.wrb.devica.common.exception.BusinessException;
import java.util.Arrays;
import lombok.Getter;

@Getter
public enum ProductCategoryCode {

    LAPTOP("노트북");

    private final String displayName;

    ProductCategoryCode(String displayName) {
        this.displayName = displayName;
    }

    public static ProductCategoryCode from(String code) {
        return Arrays.stream(values())
            .filter(category -> category.name().equals(code))
            .findFirst()
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.PRODUCT_CATEGORY_NOT_FOUND));
    }
}
