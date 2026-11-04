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
class StudentStoreAdditionalOffersMigrationTest {

    private static final String STUDENT_STORE = "학생복지스토어";
    private static final List<ExpectedOffer> EXPECTED = List.of(
        new ExpectedOffer("MDHD4KH/A", 2_813_400L, "138930"),
        new ExpectedOffer("MDVD4KH/A", 2_310_600L, "138924"),
        new ExpectedOffer("MDVC4KH/A", 3_095_200L, "138923"),
        new ExpectedOffer("Z1MH001VY", 3_906_700L, "138852"),
        new ExpectedOffer("MGE44KH/A", 4_522_600L, "138848"),
        new ExpectedOffer("MGEC4KH/A", 5_607_000L, "138847")
    );

    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbcTemplate;
    private Flyway studentMigration;

    @BeforeEach
    void 가격_등록_이전_상태로_복원한다() {
        Flyway previous = Flyway.configure().configuration(flyway.getConfiguration()).target("1.24").load();
        previous.clean();
        previous.migrate();
        studentMigration = Flyway.configure().configuration(flyway.getConfiguration()).target("1.25").load();
    }

    @AfterEach
    void 실패한_마이그레이션_이력을_다른_테스트에_남기지_않는다() {
        flyway.clean();
        flyway.migrate();
    }

    @Test
    void 추가_판매처를_보존하고_대상_제품의_검수한_가격을_등록한다() {
        // given
        ExpectedOffer first = EXPECTED.getFirst();
        long firstId = productId(first.code());
        jdbcTemplate.update("UPDATE product_offer SET price = ? WHERE product_id = ? AND status = 'ON_SALE'",
            first.price() + 100_000L, firstId);
        saveOffer(firstId, "품절 판매처", first.price() - 100_000L, "SOLD_OUT");
        ExpectedOffer second = EXPECTED.get(1);
        saveOffer(productId(second.code()), "추가 판매처", second.price() - 100_000L, "ON_SALE");
        var before = offerSnapshot();

        // when
        studentMigration.migrate();

        // then
        assertThat(offerSnapshot()).containsAll(before);
        assertNewOffersMatchTargets(before, EXPECTED.stream().map(expected -> productId(expected.code())).toList());
        EXPECTED.forEach(expected -> assertVerifiedOffer(expected, before));
    }

    @Test
    void Air의_메모리_사양이_다르면_모든_등록을_중단한다() {
        // given
        jdbcTemplate.update("UPDATE laptop SET memory_gb = 32 WHERE id = ?", productId("MDVC4KH/A"));

        // when & then
        assertMigrationRejectedWithoutChanges();
    }

    @Test
    void Pro의_18코어_CPU가_15코어_CPU로_바뀌면_모든_등록을_중단한다() {
        // given
        jdbcTemplate.update("""
            UPDATE laptop SET cpu_id = (
                SELECT id FROM cpu WHERE manufacturer = 'Apple' AND name = 'Apple M5 Pro 15-core' AND core_count = 15
            ) WHERE id = ?
            """, productId("MGEC4KH/A"));

        // when & then
        assertMigrationRejectedWithoutChanges();
    }

    @Test
    void 기존_학생몰_오퍼가_있으면_덮어쓰거나_다른_모델도_등록하지_않는다() {
        // given
        saveOffer(productId("MGE44KH/A"), STUDENT_STORE, 4_600_000L, "ON_SALE");

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
            .containsEntry("purchase_url", "https://www.univstore.com/item/" + expected.itemId());
        assertMinPrice(productId, expected.price(), before);
    }

    private void assertMigrationRejectedWithoutChanges() {
        var offersBefore = offerSnapshot();
        var pricesBefore = jdbcTemplate.queryForList("SELECT product_id, min_price FROM product_min_price ORDER BY product_id");

        assertThatThrownBy(studentMigration::migrate).isInstanceOf(FlywayException.class);

        assertThat(offerSnapshot()).isEqualTo(offersBefore);
        assertThat(jdbcTemplate.queryForList("SELECT product_id, min_price FROM product_min_price ORDER BY product_id"))
            .isEqualTo(pricesBefore);
    }

    private void assertMinPrice(long productId, long studentPrice, List<Map<String, Object>> before) {
        long existingMin = before.stream()
            .filter(offer -> ((Number) offer.get("product_id")).longValue() == productId)
            .filter(offer -> "ON_SALE".equals(offer.get("status")))
            .mapToLong(offer -> ((Number) offer.get("price")).longValue()).min().orElse(Long.MAX_VALUE);
        assertThat(jdbcTemplate.queryForObject("SELECT min_price FROM product_min_price WHERE product_id = ?",
            Long.class, productId)).isEqualTo(Math.min(studentPrice, existingMin));
    }

    private void assertNewOffersMatchTargets(List<Map<String, Object>> before, List<Long> targetIds) {
        var existingIds = before.stream().map(offer -> offer.get("id")).toList();
        var added = offerSnapshot().stream().filter(offer -> !existingIds.contains(offer.get("id"))).toList();
        assertThat(added).extracting(offer -> offer.get("product_id")).containsExactlyInAnyOrderElementsOf(targetIds);
        assertThat(added).extracting(offer -> offer.get("name")).containsOnly("학생복지스토어");
    }

    private Map<String, Object> registeredOffer(long productId) {
        return jdbcTemplate.queryForMap("""
            SELECT price, checked_at, status, external_item_id, purchase_url
            FROM product_offer WHERE product_id = ? AND name = '학생복지스토어'
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

    private record ExpectedOffer(
        String code, long price, String itemId
    ) {
    }
}
