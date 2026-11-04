package com.wrb.devica.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @Autowired private ProductOfferService offers;
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
        saveOffer(productId(EXPECTED.getFirst().code()), "추가 판매처", 100_000L, "ON_SALE");
        var before = offerSnapshot();

        // when
        proMigration.migrate();

        // then
        assertThat(offerSnapshot()).containsAll(before);
        assertNewOffersMatchTargets(before, EXPECTED.stream().map(expected -> productId(expected.code())).toList());
        EXPECTED.forEach(this::assertVerifiedOffer);
    }

    @Test
    void 모델별_최저가를_갱신하고_품절을_제외하며_누락된_캐시를_복구한다() {
        // given
        long first = productId("Z1MH001VY");
        long second = productId("Z1ML001Y3");
        long third = productId("Z1MM001CH");
        jdbcTemplate.update("UPDATE product_offer SET price = 50000000 WHERE product_id IN (?, ?) AND status = 'ON_SALE'", first, second);
        saveOffer(second, "더 싼 판매처", 4_000_000L, "ON_SALE");
        saveOffer(second, "품절 판매처", 1_000_000L, "SOLD_OUT");
        jdbcTemplate.update("DELETE FROM product_min_price WHERE product_id = ?", third);

        // when
        proMigration.migrate();

        // then
        assertLowest("Z1MH001VY", 4_190_000L, "Apple 공홈");
        assertLowest("Z1ML001Y3", 4_000_000L, "더 싼 판매처");
        assertLowestMatchesOnSaleOffers("Z1MM001CH");
        assertLowestMatchesOnSaleOffers("MGE44KH/A");
        assertLowestMatchesOnSaleOffers("MGEC4KH/A");
    }

    @Test
    void 다섯_대상_중_하나라도_없으면_모든_등록을_중단한다() {
        // given: 마지막 대상이 없어도 다른 대상을 부분 등록하지 않는다.
        jdbcTemplate.update("UPDATE product SET code = 'UNMATCHED-OFFER' WHERE code = ?", "MGEC4KH/A");

        // when & then
        assertMigrationRejectedWithoutChanges();
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

    private void assertVerifiedOffer(ExpectedOffer expected) {

        long productId = productId(expected.code());
        var apple = registeredOffer(productId);
        assertThat(apple).extracting(ProductOffer::getPrice, ProductOffer::getCheckedAt,
                ProductOffer::getStatus)
            .containsExactly(expected.price(), LocalDate.of(2026, 11, 2), OfferStatus.ON_SALE);
        assertThat(apple.getExternalItemId()).isNull();
        assertThat(apple.getPurchaseUrl()).isEqualTo(APPLE_URL + expected.path());
        assertLowestMatchesOnSaleOffers(expected.code());
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
        var pricesBefore = jdbcTemplate.queryForList("SELECT product_id, min_price FROM product_min_price ORDER BY product_id");

        assertThatThrownBy(proMigration::migrate).isInstanceOf(FlywayException.class);

        assertThat(offerSnapshot()).isEqualTo(offersBefore);
        assertThat(jdbcTemplate.queryForList("SELECT product_id, min_price FROM product_min_price ORDER BY product_id"))
            .isEqualTo(pricesBefore);
    }


    private void assertLowestMatchesOnSaleOffers(String code) {
        long id = productId(code);
        long expected = offers.findOnSaleOffers(id).stream().mapToLong(ProductOffer::getPrice).min().orElseThrow();
        assertThat(jdbcTemplate.queryForObject("SELECT min_price FROM product_min_price WHERE product_id = ?",
            Long.class, id)).isEqualTo(expected);
        assertThat(offers.findLowestOnSaleOffers(List.of(id)).get(id).getPrice()).isEqualTo(expected);
    }

    private void assertNewOffersMatchTargets(List<Map<String, Object>> before, List<Long> targetIds) {
        var existingIds = before.stream().map(offer -> offer.get("id")).toList();
        var added = offerSnapshot().stream().filter(offer -> !existingIds.contains(offer.get("id"))).toList();
        assertThat(added).extracting(offer -> offer.get("product_id")).containsExactlyInAnyOrderElementsOf(targetIds);
        assertThat(added).extracting(offer -> offer.get("name")).containsOnly("Apple 공홈");
    }

    private ProductOffer registeredOffer(long productId) {
        var registered = offers.findOnSaleOffers(productId).stream()
            .filter(offer -> "Apple 공홈".equals(offer.getName())).toList();
        assertThat(registered).hasSize(1);
        return registered.getFirst();
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
