package com.wrb.devica.category;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCategoryServiceTest {

    private final ProductCategoryService productCategoryService = new ProductCategoryService();

    @Test
    void 지원하는_제품_종류를_모두_반환한다() {
        // when
        List<ProductCategoryName> categories = productCategoryService.findAll();

        // then
        assertThat(categories).containsExactly(ProductCategoryName.values());
    }
}
