package com.wrb.devica.category;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
