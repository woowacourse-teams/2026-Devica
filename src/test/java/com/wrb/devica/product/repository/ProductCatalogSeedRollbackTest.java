package com.wrb.devica.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ProductCatalogSeedRollbackTest {

    @Test
    void 마지막_제품_코드가_중복되면_시드의_모든_삽입을_롤백한다() throws Exception {
        String port = System.getenv().getOrDefault("TEST_DB_PORT", "3307");
        var dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:" + port + "/devica_seed_rollback_test"
                + "?createDatabaseIfNotExist=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul",
            "root", "root");
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var flyway = Flyway.configure().dataSource(dataSource).cleanDisabled(false).target("1.5").load();

        flyway.clean();
        try {
            flyway.migrate();

            JsonNode products = new ObjectMapper()
                .readTree(Path.of("data", "product-catalog", "products.json").toFile())
                .path("products");
            String lastProductCode = products.get(products.size() - 1).path("product").path("code").asText();
            jdbcTemplate.update("""
                INSERT INTO product (product_category_id, brand, name, code, created_at, updated_at)
                VALUES ((SELECT id FROM product_category WHERE code = 'LAPTOP'),
                        'Collision', 'Collision', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, lastProductCode);

            var seedFlyway = Flyway.configure().dataSource(dataSource).load();
            assertThatThrownBy(seedFlyway::migrate)
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("Duplicate entry");

            assertThat(count(jdbcTemplate, "cpu")).isZero();
            assertThat(count(jdbcTemplate, "product")).isEqualTo(1);
            assertThat(count(jdbcTemplate, "laptop")).isZero();
            assertThat(count(jdbcTemplate, "product_offer")).isZero();
        } finally {
            flyway.clean();
        }
    }

    private long count(JdbcTemplate jdbcTemplate, String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
