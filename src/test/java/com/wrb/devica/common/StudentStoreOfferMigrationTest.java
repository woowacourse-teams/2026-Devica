package com.wrb.devica.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrb.devica.product.domain.OfferLinkType;
import com.wrb.devica.product.domain.OfferPriceBasis;
import com.wrb.devica.product.domain.OfferStatus;
import com.wrb.devica.product.domain.ProductOffer;
import com.wrb.devica.product.service.ProductOfferService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("flyway-test")
@Import(FlywayTestConfiguration.class)
class StudentStoreOfferMigrationTest {

    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ProductOfferService offers;
    private Flyway studentMigration;

    @BeforeEach
    void 가격_등록_이전_상태로_복원한다() {
        Flyway previous = Flyway.configure().configuration(flyway.getConfiguration()).target("1.23").load();
        previous.clean();
        previous.migrate();
        studentMigration = Flyway.configure().configuration(flyway.getConfiguration()).target("1.24").load();
    }

    @AfterEach
    void 실패한_마이그레이션_이력을_다른_테스트에_남기지_않는다() {
        flyway.clean();
        flyway.migrate();
    }

    @Test
    void 기존_가격을_보존하고_학생_인증_판매가를_최저가로_반영한다() {
        // given
        long productId = targetProductId();
        var existingBefore = existingOfferSnapshot();

        // when
        studentMigration.migrate();

        // then
        assertThat(existingBefore).hasSize(29);
        assertThat(existingOfferSnapshot()).isEqualTo(existingBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Long.class)).isEqualTo(20L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_offer", Long.class)).isEqualTo(30L);
        var onSale = offers.findOnSaleOffers(productId);
        assertThat(onSale).extracting(ProductOffer::getName).containsExactly("학생복지스토어", "쿠팡", "Apple 공홈");
        assertThat(onSale).extracting(ProductOffer::getPrice).containsExactly(2_023_600L, 2_080_500L, 2_190_000L);
        var student = onSale.getFirst();
        assertThat(student).extracting(ProductOffer::getCheckedAt, ProductOffer::getStatus,
                ProductOffer::getPriceBasis, ProductOffer::getLinkType)
            .containsExactly(LocalDate.of(2026, 11, 2), OfferStatus.ON_SALE,
                OfferPriceBasis.SELLER_PRICE, OfferLinkType.PURCHASE);
        assertThat(student.getExternalItemId()).isNull();
        assertThat(student.getPriceCondition()).isEqualTo("학생 인증 필요 · 스카이 블루 · 한국어 키보드 · MDHH4KH/A");
        assertThat(student.getPurchaseUrl()).isEqualTo("https://www.univstore.com/item/138935");
        assertThat(minPrice(productId)).isEqualTo(2_023_600L);
        assertThat(offers.findLowestOnSaleOffers(List.of(productId)).get(productId).getName())
            .isEqualTo("학생복지스토어");

        assertThat(studentMigration.migrate().migrationsExecuted).isZero();
        assertThat(offers.findOnSaleOffers(productId)).hasSize(3);
    }

    @Test
    void 더_싼_판매중_오퍼를_유지하고_품절_가격은_최저가에서_제외한다() {
        // given
        long productId = targetProductId();
        saveOffer(productId, "다른 판매처", 1_700_000L, "ON_SALE");
        saveOffer(productId, "품절 판매처", 1_000_000L, "SOLD_OUT");
        jdbcTemplate.update("UPDATE product_min_price SET min_price = 1700000 WHERE product_id = ?", productId);

        // when
        studentMigration.migrate();

        // then
        assertThat(minPrice(productId)).isEqualTo(1_700_000L);
        assertThat(offers.findLowestOnSaleOffers(List.of(productId)).get(productId).getName())
            .isEqualTo("다른 판매처");
    }

    @Test
    void 대상_제품_코드가_없으면_기존_오퍼를_보존하고_등록을_중단한다() {
        // given
        jdbcTemplate.update("UPDATE product SET code = 'UNMATCHED-APPLE' WHERE code = 'MDH74KH/A'");
        var before = existingOfferSnapshot();

        // when & then
        assertThatThrownBy(studentMigration::migrate).isInstanceOf(FlywayException.class);

        // then
        assertThat(existingOfferSnapshot()).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_offer", Long.class)).isEqualTo(29L);
    }

    @Test
    void 기존_학생몰_오퍼가_있으면_덮어쓰거나_중복_등록하지_않는다() {
        // given
        long productId = targetProductId();
        saveOffer(productId, "학생복지스토어", 2_100_000L, "ON_SALE");
        var before = jdbcTemplate.queryForList("SELECT * FROM product_offer ORDER BY id");
        long cachedPrice = minPrice(productId);

        // when & then
        assertThatThrownBy(studentMigration::migrate).isInstanceOf(FlywayException.class);

        // then
        assertThat(jdbcTemplate.queryForList("SELECT * FROM product_offer ORDER BY id")).isEqualTo(before);
        assertThat(minPrice(productId)).isEqualTo(cachedPrice);
    }

    @Test
    void 같은_제품_코드여도_메모리_사양이_다르면_등록하지_않는다() {
        // given
        jdbcTemplate.update("UPDATE laptop SET memory_gb = 24 WHERE id = ?", targetProductId());

        // when & then
        assertThatThrownBy(studentMigration::migrate).isInstanceOf(FlywayException.class);

        // then
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_offer", Long.class)).isEqualTo(29L);
    }

    private long targetProductId() {
        return jdbcTemplate.queryForObject("SELECT id FROM product WHERE code = 'MDH74KH/A'", Long.class);
    }

    private List<Map<String, Object>> existingOfferSnapshot() {
        return jdbcTemplate.queryForList(
            "SELECT * FROM product_offer WHERE name IN ('쿠팡', 'Apple 공홈') ORDER BY id");
    }

    private long minPrice(long productId) {
        return jdbcTemplate.queryForObject(
            "SELECT min_price FROM product_min_price WHERE product_id = ?", Long.class, productId);
    }

    private void saveOffer(long productId, String name, long price, String status) {
        jdbcTemplate.update("""
            INSERT INTO product_offer (product_id, name, price, purchase_url, status, created_at, updated_at)
            VALUES (?, ?, ?, 'https://example.test/product', ?, NOW(), NOW())
            """, productId, name, price, status);
    }
}
