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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("flyway-test")
@Import(FlywayTestConfiguration.class)
class MacBookAirOfferMigrationTest {

    private static final String APPLE_URL = "https://www.apple.com/kr/shop/buy-mac/macbook-air/";
    private static final List<ExpectedOffer> EXPECTED = List.of(
        new ExpectedOffer("MDHD4KH/A", 3_040_000L, 2_840_000L,
            "13-모델-스카이-블루-m5-칩-10코어-cpu-10코어-gpu-24gb-메모리-1tb-저장-장치"),
        new ExpectedOffer("MDVD4KH/A", 2_490_000L, 2_330_640L,
            "15-모델-스카이-블루-m5-칩-10코어-cpu-10코어-gpu-16gb-메모리-512gb-저장-장치"),
        new ExpectedOffer("MDVC4KH/A", 3_340_000L, 3_126_240L,
            "15-모델-스카이-블루-m5-칩-10코어-cpu-10코어-gpu-24gb-메모리-1tb-저장-장치")
    );

    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ProductOfferService offers;
    private Flyway airMigration;

    @BeforeEach
    void 가격_등록_이전_상태로_복원한다() {
        Flyway previous = Flyway.configure().configuration(flyway.getConfiguration()).target("1.21").load();
        previous.clean();
        previous.migrate();
        airMigration = Flyway.configure().configuration(flyway.getConfiguration()).target("1.22").load();
    }

    @AfterEach
    void 실패한_마이그레이션_이력을_다른_테스트에_남기지_않는다() {
        flyway.clean();
        flyway.migrate();
    }

    @Test
    void 기존_21개_오퍼를_보존하고_세_모델에_검증된_Apple_가격을_등록한다() {
        // given
        var before = offerSnapshot();

        // when
        airMigration.migrate();

        // then
        assertThat(before).hasSize(21);
        assertThat(offerSnapshot()).hasSize(24).containsAll(before);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Long.class)).isEqualTo(20L);
        EXPECTED.forEach(this::assertVerifiedOffer);
        assertLowest("MDH74KH/A", 2_080_500L, "쿠팡");
        assertThat(airMigration.migrate().migrationsExecuted).isZero();
        assertThat(offerSnapshot()).hasSize(24);
    }

    @Test
    void 세_모델의_최저가를_갱신하고_품절을_제외하며_누락된_캐시를_복구한다() {
        // given
        long first = productId("MDHD4KH/A");
        long second = productId("MDVD4KH/A");
        long third = productId("MDVC4KH/A");
        jdbcTemplate.update("UPDATE product_offer SET price = 5000000 WHERE product_id = ?", first);
        saveOffer(second, "더 싼 판매처", 2_000_000L, "ON_SALE");
        saveOffer(second, "품절 판매처", 1_000_000L, "SOLD_OUT");
        jdbcTemplate.update("DELETE FROM product_min_price WHERE product_id = ?", third);

        // when
        airMigration.migrate();

        // then
        assertLowest("MDHD4KH/A", 3_040_000L, "Apple 공홈");
        assertLowest("MDVD4KH/A", 2_000_000L, "더 싼 판매처");
        assertLowest("MDVC4KH/A", 3_126_240L, "쿠팡");
        assertLowest("MDH74KH/A", 2_080_500L, "쿠팡");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MDHD4KH/A", "MDVD4KH/A", "MDVC4KH/A"})
    void 세_대상_중_하나라도_없으면_모든_등록을_중단한다(String missingCode) {
        // given
        jdbcTemplate.update("UPDATE product SET code = 'UNMATCHED-APPLE' WHERE code = ?", missingCode);

        // when & then
        assertMigrationRejectedWithoutChanges();
    }

    @Test
    void 하나의_모델이_다른_메모리_사양이면_모든_등록을_중단한다() {
        // given
        jdbcTemplate.update("UPDATE laptop SET memory_gb = 32 WHERE id = ?", productId("MDVC4KH/A"));

        // when & then
        assertMigrationRejectedWithoutChanges();
    }

    @Test
    void 하나의_모델에_Apple_오퍼가_있으면_덮어쓰거나_다른_모델도_등록하지_않는다() {
        // given
        saveOffer(productId("MDVD4KH/A"), "Apple 공홈", 2_600_000L, "ON_SALE");

        // when & then
        assertMigrationRejectedWithoutChanges();
    }

    private void assertVerifiedOffer(ExpectedOffer expected) {
        long productId = productId(expected.code());
        var onSale = offers.findOnSaleOffers(productId);
        assertThat(onSale).extracting(ProductOffer::getName).containsExactly("쿠팡", "Apple 공홈");
        var apple = onSale.get(1);
        assertThat(apple).extracting(ProductOffer::getPrice, ProductOffer::getCheckedAt,
                ProductOffer::getStatus, ProductOffer::getPriceBasis, ProductOffer::getLinkType)
            .containsExactly(expected.price(), LocalDate.of(2026, 11, 2), OfferStatus.ON_SALE,
                OfferPriceBasis.SELLER_PRICE, OfferLinkType.PURCHASE);
        assertThat(apple.getExternalItemId()).isNull();
        assertThat(apple.getPriceCondition()).isEqualTo("일반 판매가 · 스카이 블루 · 한국어 키보드");
        assertThat(apple.getPurchaseUrl()).isEqualTo(APPLE_URL + expected.path());
        assertLowest(expected.code(), expected.coupangPrice(), "쿠팡");
    }

    private void assertLowest(String code, long price, String seller) {
        long productId = productId(code);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT min_price FROM product_min_price WHERE product_id = ?", Long.class, productId))
            .as("%s의 최저가 캐시", code).isEqualTo(price);
        var winner = offers.findLowestOnSaleOffers(List.of(productId)).get(productId);
        assertThat(winner).extracting(ProductOffer::getName, ProductOffer::getPrice).containsExactly(seller, price);
    }

    private void assertMigrationRejectedWithoutChanges() {
        var offersBefore = offerSnapshot();
        var pricesBefore = jdbcTemplate.queryForList("SELECT * FROM product_min_price ORDER BY product_id");

        assertThatThrownBy(airMigration::migrate).isInstanceOf(FlywayException.class);

        assertThat(offerSnapshot()).isEqualTo(offersBefore);
        assertThat(jdbcTemplate.queryForList("SELECT * FROM product_min_price ORDER BY product_id"))
            .isEqualTo(pricesBefore);
    }

    private long productId(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM product WHERE code = ?", Long.class, code);
    }

    private List<Map<String, Object>> offerSnapshot() {
        return jdbcTemplate.queryForList("SELECT * FROM product_offer ORDER BY id");
    }

    private void saveOffer(long productId, String name, long price, String status) {
        jdbcTemplate.update("""
            INSERT INTO product_offer (product_id, name, price, purchase_url, status, created_at, updated_at)
            VALUES (?, ?, ?, 'https://example.test/product', ?, NOW(), NOW())
            """, productId, name, price, status);
    }

    private record ExpectedOffer(String code, long price, long coupangPrice, String path) {
    }
}
