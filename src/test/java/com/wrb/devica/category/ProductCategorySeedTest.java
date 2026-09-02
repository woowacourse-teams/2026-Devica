package com.wrb.devica.category;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

// 조회는 enum 으로 하고 다른 엔티티는 테이블을 FK 로 참조하므로, 둘이 어긋나면
// 참조할 카테고리 행이 DB 에 없는 상태가 된다
@SpringBootTest
@TestPropertySource(properties = "spring.sql.init.mode=always")
class ProductCategorySeedTest {

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Test
    void 시드된_제품_종류와_enum이_일대일로_대응한다() {
        // when
        List<ProductCategoryCode> seeded = productCategoryRepository.findAll().stream()
                .map(ProductCategory::getCode)
                .toList();

        // then
        assertThat(seeded).containsExactlyInAnyOrder(ProductCategoryCode.values());
    }
}
