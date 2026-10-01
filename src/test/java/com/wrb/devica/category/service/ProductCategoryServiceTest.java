package com.wrb.devica.category.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.category.domain.ProductCategoryCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductCategoryServiceTest {

    private final ProductCategoryService productCategoryService = new ProductCategoryService();

    @Test
    void 지원하는_제품_종류를_모두_반환한다() {
        // when
        List<ProductCategoryCode> categories = productCategoryService.findAll();

        // then
        assertThat(categories).containsExactly(ProductCategoryCode.values());
    }
}
