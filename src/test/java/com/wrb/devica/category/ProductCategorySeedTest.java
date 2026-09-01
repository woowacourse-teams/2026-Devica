package com.wrb.devica.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class ProductCategorySeedTest {

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    // 애플리케이션 enum 과 DB에 저장된 카테고리가 대응되는지 확인한다.
    @Test
    void 시드로_추가한_제품_종류와_enum이_일대일로_대응한다() {
        // when
        List<ProductCategoryName> seeded = productCategoryRepository.findAll().stream()
            .map(ProductCategory::getName)
            .toList();

        // then
        assertThat(seeded).containsExactlyInAnyOrder(ProductCategoryName.values());
    }
}
