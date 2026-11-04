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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
        new ExpectedOffer("MDHD4KH/A", 2_813_400L, "138930",
            "학생 인증 필요 · 스타라이트 · 한국어 키보드 · MDHD4KH/A"),
        new ExpectedOffer("MDVD4KH/A", 2_310_600L, "138924",
            "학생 인증 필요 · 스카이 블루 · 한국어 키보드 · MDVQ4KH/A"),
        new ExpectedOffer("MDVC4KH/A", 3_095_200L, "138923",
            "학생 인증 필요 · 스카이 블루 · 한국어 키보드 · MDVU4KH/A"),
        new ExpectedOffer("Z1MH001VY", 3_906_700L, "138852",
            "학생 인증 필요 · 실버 · 스탠다드 디스플레이 · 한국어 키보드 · MGDN4KH/A"),
        new ExpectedOffer("MGE44KH/A", 4_522_600L, "138848",
            "학생 인증 필요 · 스페이스 블랙 · 스탠다드 디스플레이 · 한국어 키보드 · MGEA4KH/A"),
        new ExpectedOffer("MGEC4KH/A", 5_607_000L, "138847",
            "학생 인증 필요 · 스페이스 블랙 · 스탠다드 디스플레이 · 한국어 키보드 · MGEC4KH/A")
    );

    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ProductOfferService offers;
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
        saveOffer(productId(EXPECTED.getFirst().code()), "추가 판매처", 100_000L, "ON_SALE");
        var before = offerSnapshot();

        // when
        studentMigration.migrate();

        // then
        assertThat(offerSnapshot()).containsAll(before);
        assertNewOffersMatchTargets(before, EXPECTED.stream().map(expected -> productId(expected.code())).toList());
        EXPECTED.forEach(this::assertVerifiedOffer);
    }

    @ParameterizedTest
    @CsvSource({"MDHD4KH/A, MDVD4KH/A, MDVC4KH/A", "Z1MH001VY, MGE44KH/A, MGEC4KH/A"})
    void 학생가와_더_싼_판매처를_반영하고_품절을_제외하며_누락된_캐시를_복구한다(
        String studentWinnerCode, String cheaperCode, String missingCacheCode
    ) {
        // given
        jdbcTemplate.update("UPDATE product_offer SET price = 50000000 WHERE product_id IN (?, ?) AND status = 'ON_SALE'",
            productId(studentWinnerCode), productId(cheaperCode));
        saveOffer(productId(cheaperCode), "더 싼 판매처", 100_000L, "ON_SALE");
        saveOffer(productId(cheaperCode), "품절 판매처", 1L, "SOLD_OUT");
        jdbcTemplate.update("DELETE FROM product_min_price WHERE product_id = ?", productId(missingCacheCode));

        // when
        studentMigration.migrate();

        // then
        long studentPrice = EXPECTED.stream().filter(offer -> offer.code().equals(studentWinnerCode))
            .findFirst().orElseThrow().price();
        assertLowest(studentWinnerCode, studentPrice, STUDENT_STORE);
        assertLowest(cheaperCode, 100_000L, "더 싼 판매처");
        assertLowestMatchesOnSaleOffers(missingCacheCode);
    }

    @Test
    void 여섯_대상_중_하나라도_없으면_모든_등록을_중단한다() {
        // given: 마지막 대상이 없어도 다른 대상을 부분 등록하지 않는다.
        jdbcTemplate.update("UPDATE product SET code = 'UNMATCHED-OFFER' WHERE code = ?", "MGEC4KH/A");

        // when & then
        assertMigrationRejectedWithoutChanges();
    }

    @ParameterizedTest
    @CsvSource({"MDVC4KH/A, 32", "Z1MH001VY, 48"})
    void Air나_Pro의_메모리_사양이_다르면_모든_등록을_중단한다(String code, int memoryGb) {
        // given
        jdbcTemplate.update("UPDATE laptop SET memory_gb = ? WHERE id = ?", memoryGb, productId(code));

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

    @ParameterizedTest
    @ValueSource(strings = {"ON_SALE", "SOLD_OUT", "HIDDEN"})
    void 기존_학생몰_오퍼는_상태와_무관하게_덮어쓰거나_중복_등록하지_않는다(String status) {
        // given
        saveOffer(productId("MGE44KH/A"), STUDENT_STORE, 1_000_000L, status);

        // when & then
        assertMigrationRejectedWithoutChanges();
    }

    private void assertVerifiedOffer(ExpectedOffer expected) {

        long productId = productId(expected.code());
        var student = registeredOffer(productId);
        assertThat(student).extracting(ProductOffer::getPrice, ProductOffer::getCheckedAt,
                ProductOffer::getStatus, ProductOffer::getPriceBasis, ProductOffer::getLinkType)
            .containsExactly(expected.price(), LocalDate.of(2026, 11, 2), OfferStatus.ON_SALE,
                OfferPriceBasis.SELLER_PRICE, OfferLinkType.PURCHASE);
        assertThat(student.getExternalItemId()).isNull();
        assertThat(student.getPriceCondition()).isEqualTo(expected.condition());
        assertThat(student.getPurchaseUrl()).isEqualTo("https://www.univstore.com/item/" + expected.itemId());
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

        assertThatThrownBy(studentMigration::migrate).isInstanceOf(FlywayException.class);

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
        assertThat(added).extracting(offer -> offer.get("name")).containsOnly("학생복지스토어");
    }

    private ProductOffer registeredOffer(long productId) {
        var registered = offers.findOnSaleOffers(productId).stream()
            .filter(offer -> "학생복지스토어".equals(offer.getName())).toList();
        assertThat(registered).hasSize(1);
        return registered.getFirst();
    }

    private long productId(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM product WHERE code = ?", Long.class, code);
    }

    private List<Map<String, Object>> offerSnapshot() {
        return jdbcTemplate.queryForList("""
            SELECT id, product_id, name, price, status, purchase_url, external_item_id,
                   checked_at, price_condition, price_basis, link_type
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
        String code, long price, String itemId, String condition
    ) {
    }
}
