package com.wrb.devica.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrb.devica.product.infra.coupang.CoupangProductUrl;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @Test
    void 전체_마이그레이션을_적용하고_엔티티_매핑을_검증한다() {
        // when
        var pendingMigrations = flyway.info().pending();

        // then
        assertThat(pendingMigrations).isEmpty();
    }

    @Test
    void 등록된_노트북의_쿠팡_판매상품_ID는_URL의_vendorItemId와_일치하고_중복되지_않는다() {
        // given: 운영 DB가 아닌 마이그레이션 테스트 전용 DB에 초기 데이터를 적용한다.
        flyway.clean();
        flyway.migrate();

        // when
        var offers = jdbcTemplate.queryForList("""
            SELECT p.code, o.external_item_id, o.purchase_url
            FROM product_offer o
            JOIN product p ON p.id = o.product_id
            JOIN laptop l ON l.id = p.id
            WHERE o.seller = 'COUPANG'
            ORDER BY p.code
            """);

        // then
        assertThat(offers).isNotEmpty();
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

    @Test
    void 바꿀_쿠팡_판매상품이_같은_제품에_이미_있으면_모델_코드를_알리고_판매처를_보정하지_않는다() {
        try {
            // given: 16Z90TS-GU7WK의 옛 판매상품을 V1.29가 새 판매상품으로 바꾸기 전에 새 판매상품이 이미 등록됐다.
            migrateTo("1.28");
            long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE code = '16Z90TS-GU7WK'", Long.class);
            jdbcTemplate.update("""
                INSERT INTO product_offer
                    (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
                VALUES (?, '쿠팡', 3700000, '95599972655',
                        'https://www.coupang.com/vp/products/8670821592?itemId=25170697413&vendorItemId=95599972655',
                        'ON_SALE', '2026-11-01', NOW(), NOW())
                """, productId);

            // when & then
            assertThatThrownBy(this::migrateOnSeparateConnection)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Duplicate entry '16Z90TS-GU7WK'");
            assertThat(jdbcTemplate.queryForList("""
                SELECT external_item_id, price FROM product_offer
                WHERE product_id = ? AND name = '쿠팡' ORDER BY id
                """, productId))
                .containsExactly(
                    Map.of("external_item_id", "95514237961", "price", 3_000_000L),
                    Map.of("external_item_id", "95599972655", "price", 3_700_000L));
        } finally {
            flyway.clean();
            flyway.migrate();
        }
    }

    @Test
    void 판매처_이름을_판매처_코드로_채운다() {
        // when
        var sellers = jdbcTemplate.queryForList("SELECT DISTINCT name, seller FROM product_offer ORDER BY seller");

        // then
        assertThat(sellers).containsExactly(
            Map.of("name", "Apple 공홈", "seller", "APPLE_STORE"),
            Map.of("name", "쿠팡", "seller", "COUPANG"),
            Map.of("name", "당근", "seller", "DAANGN"),
            Map.of("name", "학생복지스토어", "seller", "STUDENT_STORE"));
    }

    @Test
    void 판매처_코드로_바꿀_수_없는_이름이_있으면_이름을_알리고_컬럼을_바꾸기_전에_중단한다() {
        try {
            // given
            migrateTo("1.29");
            long productId = saveProduct("UNKNOWN-SELLER");
            jdbcTemplate.update("""
                INSERT INTO product_offer (product_id, name, price, purchase_url, status, created_at, updated_at)
                VALUES (?, '알 수 없는 판매처', 1000000, 'https://example.com', 'ON_SALE', NOW(), NOW())
                """, productId);

            // when & then
            assertThatThrownBy(this::migrateOnSeparateConnection)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Duplicate entry '알 수 없는 판매처'");
            assertThat(hasSellerColumn()).isFalse();
        } finally {
            flyway.clean();
            flyway.migrate();
        }
    }

    @Test
    void 같은_제품_판매처_판매상품_ID_행이_이미_여럿이면_모델_코드를_알리고_컬럼을_바꾸기_전에_중단한다() {
        try {
            // given: 15번 제품에 V1.29로 보정한 쿠팡 판매상품이 한 번 더 등록돼 있다.
            migrateTo("1.29");
            jdbcTemplate.update("""
                INSERT INTO product_offer
                    (product_id, name, price, external_item_id, purchase_url, status, created_at, updated_at)
                SELECT product_id, name, price, external_item_id, purchase_url, status, NOW(), NOW()
                FROM product_offer
                WHERE product_id = (SELECT id FROM product WHERE code = '16Z90TS-GU7WK') AND name = '쿠팡'
                """);

            // when & then
            assertThatThrownBy(this::migrateOnSeparateConnection)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Duplicate entry '16Z90TS-GU7WK'");
            assertThat(hasSellerColumn()).isFalse();
        } finally {
            flyway.clean();
            flyway.migrate();
        }
    }

    // 실패한 마이그레이션의 임시 테이블이 커넥션 풀에 남지 않도록 별도 연결로 적용한다.
    private void migrateOnSeparateConnection() {
        Flyway.configure()
            .configuration(flyway.getConfiguration())
            .dataSource(datasourceUrl, datasourceUsername, datasourcePassword)
            .load()
            .migrate();
    }

    private boolean hasSellerColumn() {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'product_offer' AND column_name = 'seller'
            """, Integer.class) > 0;
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

    // 이후 V1.30이 판매처 코드로 바꿀 수 있도록 실제 판매처 이름을 쓴다.
    private void saveOffer(long productId, long price, String status) {
        jdbcTemplate.update("""
            INSERT INTO product_offer (product_id, name, price, purchase_url, status, created_at, updated_at)
            VALUES (?, '학생복지스토어', ?, 'https://example.com', ?, NOW(), NOW())
            """, productId, price, status);
    }

    private List<Long> minPriceOf(long productId) {
        return jdbcTemplate.queryForList(
            "SELECT min_price FROM product_min_price WHERE product_id = ?", Long.class, productId);
    }
}
