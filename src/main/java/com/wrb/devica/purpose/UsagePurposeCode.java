package com.wrb.devica.purpose;

import com.wrb.devica.category.ProductCategoryCode;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;

@Getter
public enum UsagePurposeCode {

    BACKEND_DEVELOPMENT(ProductCategoryCode.LAPTOP, "백엔드 개발");

    private final ProductCategoryCode category;
    private final String displayName;

    UsagePurposeCode(ProductCategoryCode category, String displayName) {
        this.category = category;
        this.displayName = displayName;
    }

    public static List<UsagePurposeCode> findByCategory(ProductCategoryCode category) {
        return Arrays.stream(values())
                .filter(purpose -> purpose.category == category)
                .toList();
    }
}
