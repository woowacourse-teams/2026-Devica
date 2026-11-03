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
class AppleManufacturerOfferMigrationTest {

    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ProductOfferService offers;
    private Flyway appleMigration;

    @BeforeEach
    void 가격_등록_이전_상태로_복원한다() {
        Flyway previous = Flyway.configure().configuration(flyway.getConfiguration()).target("1.20").load();
        previous.clean();
        previous.migrate();
        appleMigration = Flyway.configure().configuration(flyway.getConfiguration()).target("1.21").load();
    }

    @AfterEach
    void 실패한_마이그레이션_이력을_다른_테스트에_남기지_않는다() {
        flyway.clean();
        flyway.migrate();
    }

    @Test
    void 검증된_Apple_한_건만_추가하고_쿠팡_20건과_최저가_출처를_보존한다() {
        // given
        long productId = targetProductId();
        var coupangBefore = coupangSnapshot();

        // when
        appleMigration.migrate();

        // then
        assertThat(coupangBefore).hasSize(20);
        assertThat(coupangSnapshot()).isEqualTo(coupangBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Long.class)).isEqualTo(20L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_offer", Long.class)).isEqualTo(21L);
        var onSale = offers.findOnSaleOffers(productId);
        assertThat(onSale).extracting(ProductOffer::getName).containsExactly("쿠팡", "Apple 공홈");
        var apple = onSale.get(1);
        assertThat(apple).extracting(ProductOffer::getPrice, ProductOffer::getCheckedAt,
                ProductOffer::getStatus, ProductOffer::getPriceBasis, ProductOffer::getLinkType)
            .containsExactly(2_190_000L, LocalDate.of(2026, 11, 2), OfferStatus.ON_SALE,
                OfferPriceBasis.SELLER_PRICE, OfferLinkType.PURCHASE);
        assertThat(apple.getExternalItemId()).isNull();
        assertThat(apple.getPriceCondition()).isEqualTo("일반 판매가 · 스카이 블루 · 한국어 키보드");
        assertThat(apple.getPurchaseUrl()).isEqualTo("https://www.apple.com/kr/shop/buy-mac/macbook-air/"
            + "13-모델-스카이-블루-m5-칩-10코어-cpu-8코어-gpu-16gb-메모리-512gb-저장-장치");
        assertThat(minPrice(productId)).isEqualTo(2_080_500L);
        assertThat(offers.findLowestOnSaleOffers(List.of(productId)).get(productId).getName()).isEqualTo("쿠팡");

        assertThat(appleMigration.migrate().migrationsExecuted).isZero();
        assertThat(offers.findOnSaleOffers(productId)).hasSize(2);
    }

    @Test
    void 최저가_캐시가_없어도_더_싼_판매중_오퍼를_반영해_복구한다() {
        // given
        long productId = targetProductId();
        saveOffer(productId, "다른 판매처", 1_700_000L, "ON_SALE");
        saveOffer(productId, "품절 판매처", 1_000_000L, "SOLD_OUT");
        jdbcTemplate.update("DELETE FROM product_min_price WHERE product_id = ?", productId);

        // when
        appleMigration.migrate();

        // then
        assertThat(minPrice(productId)).isEqualTo(1_700_000L);
        assertThat(offers.findLowestOnSaleOffers(List.of(productId)).get(productId).getName())
            .isEqualTo("다른 판매처");
    }

    @Test
    void 대상_제품_코드가_없으면_다른_제품에_가격을_연결하지_않는다() {
        // given
        jdbcTemplate.update("UPDATE product SET code = 'UNMATCHED-APPLE' WHERE code = 'MDH74KH/A'");
        var before = coupangSnapshot();

        // when & then
        assertThatThrownBy(appleMigration::migrate).isInstanceOf(FlywayException.class);

        // then
        assertThat(coupangSnapshot()).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_offer", Long.class)).isEqualTo(20L);
    }

    @Test
    void 기존_Apple_오퍼가_있으면_덮어쓰거나_중복_등록하지_않는다() {
        // given
        long productId = targetProductId();
        saveOffer(productId, "Apple 공홈", 2_300_000L, "ON_SALE");
        var before = jdbcTemplate.queryForList("SELECT * FROM product_offer ORDER BY id");
        long cachedPrice = minPrice(productId);

        // when & then
        assertThatThrownBy(appleMigration::migrate).isInstanceOf(FlywayException.class);

        // then
        assertThat(jdbcTemplate.queryForList("SELECT * FROM product_offer ORDER BY id")).isEqualTo(before);
        assertThat(minPrice(productId)).isEqualTo(cachedPrice);
    }

    @Test
    void 같은_제품_코드여도_메모리_사양이_다르면_등록하지_않는다() {
        // given
        jdbcTemplate.update("UPDATE laptop SET memory_gb = 24 WHERE id = ?", targetProductId());

        // when & then
        assertThatThrownBy(appleMigration::migrate).isInstanceOf(FlywayException.class);

        // then
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_offer", Long.class)).isEqualTo(20L);
    }

    private long targetProductId() {
        return jdbcTemplate.queryForObject("SELECT id FROM product WHERE code = 'MDH74KH/A'", Long.class);
    }

    private List<Map<String, Object>> coupangSnapshot() {
        return jdbcTemplate.queryForList("SELECT * FROM product_offer WHERE name = '쿠팡' ORDER BY id");
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
