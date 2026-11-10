package com.wrb.devica.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrb.devica.common.FlywayTestConfiguration;
import com.wrb.devica.product.domain.Seller;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// 마이그레이션이 만든 실제 유니크 인덱스를 검증한다. 인덱스의 세 키(제품, 판매처, 판매상품 ID)를 하나씩 바꿔 본다.
@SpringBootTest
@ActiveProfiles("flyway-test")
@Import(FlywayTestConfiguration.class)
@Transactional
class ProductOfferUniquenessTest {

    private static final Seller SELLER = Seller.COUPANG;
    private static final Seller OTHER_SELLER = Seller.STUDENT_STORE;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 같은_제품_판매처_판매상품_ID는_중복_등록할_수_없다() {
        // given
        long productId = saveProduct("OFFER-UNIQUE");
        saveOffer(productId, SELLER, "ITEM-1");

        // when & then
        assertThatThrownBy(() -> saveOffer(productId, SELLER, "ITEM-1"))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("Duplicate entry");
    }

    @Test
    void 기존_행을_이미_등록된_판매상품_ID로_변경할_수_없다() {
        // given
        long productId = saveProduct("OFFER-UNIQUE");
        long oldOfferId = saveOffer(productId, SELLER, "OLD-ITEM");
        saveOffer(productId, SELLER, "NEW-ITEM");

        // when & then
        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE product_offer SET external_item_id = 'NEW-ITEM' WHERE id = ?", oldOfferId))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("Duplicate entry");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT external_item_id FROM product_offer WHERE id = ?", String.class, oldOfferId))
            .isEqualTo("OLD-ITEM");
    }

    @Test
    void 같은_제품_판매처라도_판매상품_ID가_다르면_등록할_수_있다() {
        // given
        long productId = saveProduct("OFFER-UNIQUE");
        saveOffer(productId, SELLER, "ITEM-1");

        // when
        saveOffer(productId, SELLER, "ITEM-2");

        // then
        assertThat(offerCount(productId)).isEqualTo(2);
    }

    @Test
    void 제품이_다르면_같은_판매처_판매상품_ID를_등록할_수_있다() {
        // given
        long firstProductId = saveProduct("OFFER-UNIQUE-FIRST");
        long secondProductId = saveProduct("OFFER-UNIQUE-SECOND");
        saveOffer(firstProductId, SELLER, "ITEM-1");

        // when
        saveOffer(secondProductId, SELLER, "ITEM-1");

        // then
        assertThat(offerCount(firstProductId)).isEqualTo(1);
        assertThat(offerCount(secondProductId)).isEqualTo(1);
    }

    @Test
    void 판매처가_다르면_같은_제품_판매상품_ID를_등록할_수_있다() {
        // given
        long productId = saveProduct("OFFER-UNIQUE");
        saveOffer(productId, SELLER, "ITEM-1");

        // when
        saveOffer(productId, OTHER_SELLER, "ITEM-1");

        // then
        assertThat(offerCount(productId)).isEqualTo(2);
    }

    @Test
    void 판매상품_ID가_없으면_같은_제품_판매처에_여러_행을_등록할_수_있다() {
        // given
        long productId = saveProduct("OFFER-UNIQUE");
        saveOffer(productId, OTHER_SELLER, null);

        // when
        saveOffer(productId, OTHER_SELLER, null);

        // then
        assertThat(offerCount(productId)).isEqualTo(2);
    }

    private long saveProduct(String code) {
        jdbcTemplate.update("""
            INSERT INTO product (product_category_id, brand, name, code, created_at, updated_at)
            SELECT id, '테스트 브랜드', ?, ?, NOW(), NOW() FROM product_category WHERE code = 'LAPTOP'
            """, code, code);
        return jdbcTemplate.queryForObject("SELECT id FROM product WHERE code = ?", Long.class, code);
    }

    private long saveOffer(long productId, Seller seller, String externalItemId) {
        jdbcTemplate.update("""
            INSERT INTO product_offer
                (product_id, seller, name, price, external_item_id, purchase_url, status, created_at, updated_at)
            VALUES (?, ?, '판매처', 1000000, ?, 'https://example.com/offer', 'ON_SALE', NOW(), NOW())
            """, productId, seller.name(), externalItemId);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private int offerCount(long productId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_offer WHERE product_id = ?", Integer.class, productId);
    }
}
