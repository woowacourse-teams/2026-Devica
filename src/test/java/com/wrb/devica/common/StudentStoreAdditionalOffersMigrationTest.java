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
        new ExpectedOffer("MDHD4KH/A", 2_813_400L, 2_813_400L, STUDENT_STORE, "138930",
            "학생 인증 필요 · 스타라이트 · 한국어 키보드 · MDHD4KH/A"),
        new ExpectedOffer("MDVD4KH/A", 2_310_600L, 2_310_600L, STUDENT_STORE, "138924",
            "학생 인증 필요 · 스카이 블루 · 한국어 키보드 · MDVQ4KH/A"),
        new ExpectedOffer("MDVC4KH/A", 3_095_200L, 3_095_200L, STUDENT_STORE, "138923",
            "학생 인증 필요 · 스카이 블루 · 한국어 키보드 · MDVU4KH/A"),
        new ExpectedOffer("Z1MH001VY", 3_906_700L, 3_826_900L, "쿠팡", "138852",
            "학생 인증 필요 · 실버 · 스탠다드 디스플레이 · 한국어 키보드 · MGDN4KH/A"),
        new ExpectedOffer("MGE44KH/A", 4_522_600L, 4_491_000L, "쿠팡", "138848",
            "학생 인증 필요 · 스페이스 블랙 · 스탠다드 디스플레이 · 한국어 키보드 · MGEA4KH/A"),
        new ExpectedOffer("MGEC4KH/A", 5_607_000L, 5_566_770L, "쿠팡", "138847",
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
    void 기존_30개_오퍼를_보존하고_검수한_여섯_모델에만_학생몰_판매가를_등록한다() {
        // given
        var before = offerSnapshot();

        // when
        studentMigration.migrate();

        // then
        assertThat(before).hasSize(30);
        assertThat(offerSnapshot()).hasSize(36).containsAll(before);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Long.class)).isEqualTo(20L);
        EXPECTED.forEach(this::assertVerifiedOffer);
        assertLowest("MDH74KH/A", 2_023_600L, STUDENT_STORE);
        Map.of("MDE14KH/A", 3_047_370L, "Z1KN0001B", 3_321_990L, "Z1KN0001E", 3_970_000L,
            "Z1ML001Y3", 4_846_900L, "Z1MM001CH", 5_961_000L)
            .forEach((code, price) -> assertLowest(code, price, "쿠팡"));
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM product_offer o JOIN product p ON p.id = o.product_id
            WHERE o.name = '학생복지스토어' AND p.code IN ('Z1ML001Y3', 'Z1MM001CH', 'UM3406GA-QL151W')
            """, Long.class)).isZero();
        assertThat(studentMigration.migrate().migrationsExecuted).isZero();
        assertThat(offerSnapshot()).hasSize(36);
    }

    @ParameterizedTest
    @CsvSource({
        "MDHD4KH/A, 2813400, MDVD4KH/A, 2000000, MDVC4KH/A, 3095200, 학생복지스토어",
        "Z1MH001VY, 3906700, MGE44KH/A, 4000000, MGEC4KH/A, 5566770, 쿠팡"
    })
    void Air와_Pro의_학생가와_더_싼_판매중_오퍼를_반영하고_품절을_제외하며_누락된_캐시를_복구한다(
        String studentWinnerCode, long studentPrice, String cheaperCode, long cheaperPrice,
        String missingCacheCode, long recoveredPrice, String recoveredSeller
    ) {
        // given
        jdbcTemplate.update("UPDATE product_offer SET price = 5000000 WHERE product_id = ? AND name = '쿠팡'",
            productId(studentWinnerCode));
        saveOffer(productId(cheaperCode), "더 싼 판매처", cheaperPrice, "ON_SALE");
        saveOffer(productId(cheaperCode), "품절 판매처", 1_000_000L, "SOLD_OUT");
        jdbcTemplate.update("DELETE FROM product_min_price WHERE product_id = ?", productId(missingCacheCode));

        // when
        studentMigration.migrate();

        // then
        assertLowest(studentWinnerCode, studentPrice, STUDENT_STORE);
        assertLowest(cheaperCode, cheaperPrice, "더 싼 판매처");
        assertLowest(missingCacheCode, recoveredPrice, recoveredSeller);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "MDHD4KH/A", "MDVD4KH/A", "MDVC4KH/A", "Z1MH001VY", "MGE44KH/A", "MGEC4KH/A"
    })
    void 여섯_대상_중_하나라도_없으면_모든_등록을_중단한다(String missingCode) {
        // given
        jdbcTemplate.update("UPDATE product SET code = 'UNMATCHED-STUDENT' WHERE code = ?", missingCode);

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
    @CsvSource({
        "MDVD4KH/A, ON_SALE",
        "MDVD4KH/A, SOLD_OUT",
        "MDVD4KH/A, HIDDEN",
        "MGE44KH/A, ON_SALE",
        "MGE44KH/A, SOLD_OUT",
        "MGE44KH/A, HIDDEN"
    })
    void Air나_Pro에_학생몰_오퍼가_이미_있으면_덮어쓰거나_다른_모델도_등록하지_않는다(String code, String status) {
        // given
        saveOffer(productId(code), STUDENT_STORE, 1_000_000L, status);

        // when & then
        assertMigrationRejectedWithoutChanges();
    }

    private void assertVerifiedOffer(ExpectedOffer expected) {
        long productId = productId(expected.code());
        var onSale = offers.findOnSaleOffers(productId);
        List<String> expectedOrder = List.of("쿠팡", STUDENT_STORE, "Apple 공홈");
        if (STUDENT_STORE.equals(expected.lowestSeller())) {
            expectedOrder = List.of(STUDENT_STORE, "쿠팡", "Apple 공홈");
        }
        assertThat(onSale).extracting(ProductOffer::getName).containsExactlyElementsOf(expectedOrder);
        var student = onSale.stream()
            .filter(offer -> STUDENT_STORE.equals(offer.getName()))
            .findFirst().orElseThrow();
        assertThat(student).extracting(ProductOffer::getPrice, ProductOffer::getCheckedAt,
                ProductOffer::getStatus, ProductOffer::getPriceBasis, ProductOffer::getLinkType)
            .containsExactly(expected.price(), LocalDate.of(2026, 11, 2), OfferStatus.ON_SALE,
                OfferPriceBasis.SELLER_PRICE, OfferLinkType.PURCHASE);
        assertThat(student.getExternalItemId()).isNull();
        assertThat(student.getPriceCondition()).isEqualTo(expected.condition());
        assertThat(student.getPurchaseUrl()).isEqualTo("https://www.univstore.com/item/" + expected.itemId());
        assertLowest(expected.code(), expected.lowestPrice(), expected.lowestSeller());
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

        assertThatThrownBy(studentMigration::migrate).isInstanceOf(FlywayException.class);

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

    private record ExpectedOffer(
        String code, long price, long lowestPrice, String lowestSeller, String itemId, String condition
    ) {
    }
}
