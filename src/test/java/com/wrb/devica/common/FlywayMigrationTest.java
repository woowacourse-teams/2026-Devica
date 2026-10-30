package com.wrb.devica.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.product.infra.coupang.CoupangProductUrl;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("flyway-test")
@Import(FlywayTestConfiguration.class)
public class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 전체_마이그레이션을_적용하고_엔티티_매핑을_검증한다() {
        // when
        var pendingMigrations = flyway.info().pending();

        // then
        assertThat(pendingMigrations).isEmpty();
    }

    @Test
    void 초기_노트북_20개의_쿠팡_판매상품_ID는_URL의_vendorItemId와_일치하고_중복되지_않는다() {
        // given: 운영 DB가 아닌 마이그레이션 테스트 전용 DB에 초기 데이터를 적용한다.
        flyway.clean();
        flyway.migrate();

        // when
        var offers = jdbcTemplate.queryForList("""
            SELECT p.code, o.external_item_id, o.purchase_url
            FROM product_offer o
            JOIN product p ON p.id = o.product_id
            JOIN laptop l ON l.id = p.id
            WHERE o.name = '쿠팡'
            ORDER BY p.code
            """);

        // then
        assertThat(offers).hasSize(20);
        assertThat(offers).extracting(row -> row.get("code")).doesNotHaveDuplicates();
        assertThat(offers).extracting(row -> row.get("external_item_id")).doesNotHaveDuplicates();
        offers.forEach(row -> {
            String externalItemId = (String) row.get("external_item_id");
            var url = CoupangProductUrl.parse((String) row.get("purchase_url"));
            assertThat(url).as("%s의 쿠팡 URL", row.get("code")).isPresent();
            assertThat(externalItemId).as("%s의 판매상품 ID", row.get("code"))
                .isNotBlank()
                .isEqualTo(url.orElseThrow().vendorItemId());
        });
    }

    @Test
    void 최저가를_채우면_판매_중인_오퍼의_최솟값만_넣고_판매_중인_오퍼가_없는_제품은_비워둔다() {
        // given
        migrateTo("1.10");
        long onSale = saveProduct("MIN-PRICE-ON-SALE");
        saveOffer(onSale, 2_000_000L, "ON_SALE");
        saveOffer(onSale, 1_500_000L, "ON_SALE");
        saveOffer(onSale, 1_000_000L, "SOLD_OUT");
        long soldOutOnly = saveProduct("MIN-PRICE-SOLD-OUT");
        saveOffer(soldOutOnly, 900_000L, "SOLD_OUT");

        // when
        flyway.migrate();

        // then
        assertThat(minPriceOf(onSale)).containsExactly(1_500_000L);
        assertThat(minPriceOf(soldOutOnly)).isEmpty();
    }

    private void migrateTo(String version) {
        Flyway partial = Flyway.configure()
            .configuration(flyway.getConfiguration())
            .target(version)
            .load();
        partial.clean();
        partial.migrate();
    }

    private long saveProduct(String code) {
        jdbcTemplate.update("""
            INSERT INTO product (product_category_id, brand, name, code, created_at, updated_at)
            SELECT id, 'Brand', ?, ?, NOW(), NOW() FROM product_category WHERE code = 'LAPTOP'
            """, code, code);
        return jdbcTemplate.queryForObject("SELECT id FROM product WHERE code = ?", Long.class, code);
    }

    private void saveOffer(long productId, long price, String status) {
        jdbcTemplate.update("""
            INSERT INTO product_offer (product_id, name, price, purchase_url, status, created_at, updated_at)
            VALUES (?, '판매처', ?, 'https://example.com', ?, NOW(), NOW())
            """, productId, price, status);
    }

    private List<Long> minPriceOf(long productId) {
        return jdbcTemplate.queryForList(
            "SELECT min_price FROM product_min_price WHERE product_id = ?", Long.class, productId);
    }
}
