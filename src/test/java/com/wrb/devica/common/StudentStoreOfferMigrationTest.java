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
    void 추가_판매처가_있어도_기존_오퍼를_보존하고_검수한_가격을_등록한다() {
        // given: 판매처가 늘어나도 기존 가격을 덮어쓰지 않는다.
        long productId = targetProductId();
        saveOffer(productId, "추가 판매처", 100_000L, "ON_SALE");
        var before = offerSnapshot();

        // when
        studentMigration.migrate();

        // then
        assertThat(offerSnapshot()).containsAll(before);
        assertNewOffersMatchTargets(before, List.of(productId));
        var student = registeredOffer(productId);
        assertThat(student.getPrice()).isEqualTo(2_023_600L);
        assertThat(student).extracting(ProductOffer::getCheckedAt, ProductOffer::getStatus)
            .containsExactly(LocalDate.of(2026, 11, 2), OfferStatus.ON_SALE);
        assertThat(student.getExternalItemId()).isNull();
        assertThat(student.getPurchaseUrl()).isEqualTo("https://www.univstore.com/item/138935");
        assertThat(minPrice(productId)).isEqualTo(100_000L);
        assertThat(offers.findLowestOnSaleOffers(List.of(productId)).get(productId).getName())
            .isEqualTo("추가 판매처");
    }

    @Test
    void 더_싼_판매중_오퍼를_유지하고_품절_가격은_최저가에서_제외한다() {
        // given
        long productId = targetProductId();
        jdbcTemplate.update("UPDATE product_offer SET price = 50000000 WHERE product_id = ? AND status = 'ON_SALE'",
            productId);
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

        // when & then
        assertMigrationRejectedWithoutChanges();
    }

    @Test
    void 기존_학생몰_오퍼가_있으면_덮어쓰거나_중복_등록하지_않는다() {
        // given
        saveOffer(targetProductId(), "학생복지스토어", 2_300_000L, "ON_SALE");

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

        assertThatThrownBy(studentMigration::migrate).isInstanceOf(FlywayException.class);

        assertThat(offerSnapshot()).isEqualTo(before);
        assertThat(jdbcTemplate.queryForList("SELECT product_id, min_price FROM product_min_price ORDER BY product_id"))
            .isEqualTo(prices);
    }

    private void assertNewOffersMatchTargets(List<Map<String, Object>> before, List<Long> targetIds) {
        var existingIds = before.stream().map(offer -> offer.get("id")).toList();
        var added = offerSnapshot().stream().filter(offer -> !existingIds.contains(offer.get("id"))).toList();
        assertThat(added).extracting(offer -> offer.get("product_id")).containsExactlyInAnyOrderElementsOf(targetIds);
        assertThat(added).extracting(offer -> offer.get("name")).containsOnly("학생복지스토어");
    }

    private ProductOffer registeredOffer(long productId) {
        var registered = offers.findOnSaleOffers(productId).stream()
            .filter(offer -> "학생복지스토어".equals(offer.getName())).toList();
        assertThat(registered).hasSize(1);
        return registered.getFirst();
    }

    private long targetProductId() {
        return jdbcTemplate.queryForObject("SELECT id FROM product WHERE code = 'MDH74KH/A'", Long.class);
    }

    private List<Map<String, Object>> offerSnapshot() {
        return jdbcTemplate.queryForList("""
            SELECT id, product_id, name, price, status, purchase_url, external_item_id,
                   checked_at
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
