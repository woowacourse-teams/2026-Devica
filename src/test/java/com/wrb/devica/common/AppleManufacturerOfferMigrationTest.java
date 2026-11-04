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
    void 추가_판매처가_있어도_기존_오퍼를_보존하고_검수한_가격을_등록한다() {
        // given: 판매처가 늘어나도 기존 가격을 덮어쓰지 않는다.
        long productId = targetProductId();
        saveOffer(productId, "추가 판매처", 100_000L, "ON_SALE");
        var before = offerSnapshot();

        // when
        appleMigration.migrate();

        // then
        assertThat(offerSnapshot()).containsAll(before);
        assertNewOffersMatchTargets(before, List.of(productId));
        var apple = registeredOffer(productId);
        assertThat(apple).extracting(ProductOffer::getPrice, ProductOffer::getCheckedAt,
                ProductOffer::getStatus, ProductOffer::getPriceBasis, ProductOffer::getLinkType)
            .containsExactly(2_190_000L, LocalDate.of(2026, 11, 2), OfferStatus.ON_SALE,
                OfferPriceBasis.SELLER_PRICE, OfferLinkType.PURCHASE);
        assertThat(apple.getExternalItemId()).isNull();
        assertThat(apple.getPriceCondition()).isEqualTo("일반 판매가 · 스카이 블루 · 한국어 키보드");
        assertThat(apple.getPurchaseUrl()).isEqualTo("https://www.apple.com/kr/shop/buy-mac/macbook-air/"
            + "13-모델-스카이-블루-m5-칩-10코어-cpu-8코어-gpu-16gb-메모리-512gb-저장-장치");
        assertThat(minPrice(productId)).isEqualTo(100_000L);
        assertThat(offers.findLowestOnSaleOffers(List.of(productId)).get(productId).getName())
            .isEqualTo("추가 판매처");
    }

    @Test
    void 최저가_캐시가_없어도_더_싼_판매중_오퍼를_반영해_복구한다() {
        // given
        long productId = targetProductId();
        jdbcTemplate.update("UPDATE product_offer SET price = 50000000 WHERE product_id = ? AND status = 'ON_SALE'",
            productId);
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

        // when & then
        assertMigrationRejectedWithoutChanges();
    }

    @Test
    void 기존_Apple_오퍼가_있으면_덮어쓰거나_중복_등록하지_않는다() {
        // given
        saveOffer(targetProductId(), "Apple 공홈", 2_300_000L, "ON_SALE");

        // when & then
        assertMigrationRejectedWithoutChanges();
    }

    @Test
    void 같은_제품_코드여도_메모리_사양이_다르면_등록하지_않는다() {
        // given
        jdbcTemplate.update("UPDATE laptop SET memory_gb = 24 WHERE id = ?", targetProductId());

        // when & then
        assertMigrationRejectedWithoutChanges();
    }


    private void assertMigrationRejectedWithoutChanges() {
        var before = offerSnapshot();
        var prices = jdbcTemplate.queryForList("SELECT product_id, min_price FROM product_min_price ORDER BY product_id");

        assertThatThrownBy(appleMigration::migrate).isInstanceOf(FlywayException.class);

        assertThat(offerSnapshot()).isEqualTo(before);
        assertThat(jdbcTemplate.queryForList("SELECT product_id, min_price FROM product_min_price ORDER BY product_id"))
            .isEqualTo(prices);
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

    private long targetProductId() {
        return jdbcTemplate.queryForObject("SELECT id FROM product WHERE code = 'MDH74KH/A'", Long.class);
    }

    private List<Map<String, Object>> offerSnapshot() {
        return jdbcTemplate.queryForList("""
            SELECT id, product_id, name, price, status, purchase_url, external_item_id,
                   checked_at, price_condition, price_basis, link_type
            FROM product_offer ORDER BY id
            """);
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
