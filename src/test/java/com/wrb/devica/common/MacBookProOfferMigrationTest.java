package com.wrb.devica.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
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
class MacBookProOfferMigrationTest {

    private static final String APPLE_URL = "https://www.apple.com/kr/shop/buy-mac/macbook-pro/";
    private static final List<ExpectedOffer> EXPECTED = List.of(
        new ExpectedOffer("Z1MH001VY", 4_190_000L,
            "14-모델-space-black-스탠다드-디스플레이-apple-m5-pro-칩-15코어-cpu-16코어-gpu-24gb-메모리-1tb-저장-장치"),
        new ExpectedOffer("Z1ML001Y3", 5_210_000L,
            "14-모델-space-black-스탠다드-디스플레이-apple-m5-pro-칩-15코어-cpu-16코어-gpu-48gb-메모리-1tb-저장-장치"),
        new ExpectedOffer("Z1MM001CH", 6_510_000L,
            "14-모델-space-black-스탠다드-디스플레이-apple-m5-pro-칩-18코어-cpu-20코어-gpu-48gb-메모리-2tb-저장-장치"),
        new ExpectedOffer("MGE44KH/A", 4_990_000L,
            "16-모델-space-black-스탠다드-디스플레이-apple-m5-pro-칩-18코어-cpu-20코어-gpu-24gb-메모리-1tb-저장-장치"),
        new ExpectedOffer("MGEC4KH/A", 6_010_000L,
            "16-모델-space-black-스탠다드-디스플레이-apple-m5-pro-칩-18코어-cpu-20코어-gpu-48gb-메모리-1tb-저장-장치")
    );

    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbcTemplate;
    private Flyway proMigration;

    @BeforeEach
    void 가격_등록_이전_상태로_복원한다() {
        Flyway previous = Flyway.configure().configuration(flyway.getConfiguration()).target("1.22").load();
        previous.clean();
        previous.migrate();
        proMigration = Flyway.configure().configuration(flyway.getConfiguration()).target("1.23").load();
    }

    @AfterEach
    void 실패한_마이그레이션_이력을_다른_테스트에_남기지_않는다() {
        flyway.clean();
        flyway.migrate();
    }

    @Test
    void 추가_판매처를_보존하고_대상_제품의_검수한_가격을_등록한다() {
        // given
        long first = productId(EXPECTED.getFirst().code());
        jdbcTemplate.update("UPDATE product_offer SET price = 50000000 WHERE product_id = ? AND status = 'ON_SALE'", first);
        saveOffer(first, "품절 판매처", 1L, "SOLD_OUT");
        saveOffer(productId(EXPECTED.get(1).code()), "추가 판매처", 100_000L, "ON_SALE");
        var before = offerSnapshot();

        // when
        proMigration.migrate();

        // then
        assertThat(offerSnapshot()).containsAll(before);
        assertNewOffersMatchTargets(before, EXPECTED.stream().map(expected -> productId(expected.code())).toList());
        EXPECTED.forEach(expected -> assertVerifiedOffer(expected, before));
    }

    @Test
    void 하나의_모델이_18코어가_아닌_15코어_CPU이면_모든_등록을_중단한다() {
        // given
        jdbcTemplate.update("""
            UPDATE laptop SET cpu_id = (
                SELECT id FROM cpu WHERE name = 'Apple M5 Pro 15-core' AND core_count = 15
            ) WHERE id = ?
            """, productId("MGEC4KH/A"));

        // when & then
        assertMigrationRejectedWithoutChanges();
    }

    @Test
    void 하나의_모델에_Apple_오퍼가_있으면_덮어쓰거나_다른_모델도_등록하지_않는다() {
        // given
        saveOffer(productId("Z1MM001CH"), "Apple 공홈", 6_600_000L, "ON_SALE");

        // when & then
        assertMigrationRejectedWithoutChanges();
    }

    private void assertVerifiedOffer(ExpectedOffer expected, List<Map<String, Object>> before) {
        long productId = productId(expected.code());
        assertThat(registeredOffer(productId))
            .containsEntry("price", expected.price())
            .containsEntry("checked_at", Date.valueOf("2026-11-02"))
            .containsEntry("status", "ON_SALE")
            .containsEntry("external_item_id", null)
            .containsEntry("purchase_url", APPLE_URL + expected.path());
        assertMinPrice(productId, expected.price(), before);
    }

    private void assertMigrationRejectedWithoutChanges() {
        var offersBefore = offerSnapshot();
        var pricesBefore = jdbcTemplate.queryForList("SELECT product_id, min_price FROM product_min_price ORDER BY product_id");

        assertThatThrownBy(proMigration::migrate).isInstanceOf(FlywayException.class);

        assertThat(offerSnapshot()).isEqualTo(offersBefore);
        assertThat(jdbcTemplate.queryForList("SELECT product_id, min_price FROM product_min_price ORDER BY product_id"))
            .isEqualTo(pricesBefore);
    }

    private void assertMinPrice(long productId, long applePrice, List<Map<String, Object>> before) {
        long existingMin = before.stream()
            .filter(offer -> ((Number) offer.get("product_id")).longValue() == productId)
            .filter(offer -> "ON_SALE".equals(offer.get("status")))
            .mapToLong(offer -> ((Number) offer.get("price")).longValue()).min().orElse(Long.MAX_VALUE);
        assertThat(jdbcTemplate.queryForObject("SELECT min_price FROM product_min_price WHERE product_id = ?",
            Long.class, productId)).isEqualTo(Math.min(applePrice, existingMin));
    }

    private void assertNewOffersMatchTargets(List<Map<String, Object>> before, List<Long> targetIds) {
        var existingIds = before.stream().map(offer -> offer.get("id")).toList();
        var added = offerSnapshot().stream().filter(offer -> !existingIds.contains(offer.get("id"))).toList();
        assertThat(added).extracting(offer -> offer.get("product_id")).containsExactlyInAnyOrderElementsOf(targetIds);
        assertThat(added).extracting(offer -> offer.get("name")).containsOnly("Apple 공홈");
    }

    private Map<String, Object> registeredOffer(long productId) {
        return jdbcTemplate.queryForMap("""
            SELECT price, checked_at, status, external_item_id, purchase_url
            FROM product_offer WHERE product_id = ? AND name = 'Apple 공홈'
            """, productId);
    }

    private long productId(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM product WHERE code = ?", Long.class, code);
    }

    private List<Map<String, Object>> offerSnapshot() {
        return jdbcTemplate.queryForList("""
            SELECT id, product_id, name, price, status, purchase_url, external_item_id,
                   checked_at
            FROM product_offer ORDER BY id
            """);
    }

    private void saveOffer(long productId, String name, long price, String status) {
        jdbcTemplate.update("""
            INSERT INTO product_offer (product_id, name, price, purchase_url, status, created_at, updated_at)
            VALUES (?, ?, ?, 'https://example.test/product', ?, NOW(), NOW())
            """, productId, name, price, status);
    }

    private record ExpectedOffer(String code, long price, String path) {
    }
}
