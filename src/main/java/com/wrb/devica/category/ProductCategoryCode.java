package com.wrb.devica.category;

import com.wrb.devica.common.BusinessException;
import com.wrb.devica.common.ErrorCode;
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
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND));
    }
}
