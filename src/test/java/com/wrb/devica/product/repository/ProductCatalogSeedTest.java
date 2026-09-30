package com.wrb.devica.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrb.devica.common.FlywayTestConfiguration;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("flyway-test")
@Import(FlywayTestConfiguration.class)
@AutoConfigureMockMvc
class ProductCatalogSeedTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 제품_시드의_건수와_관계를_검증한다() {
        assertThat(count("SELECT COUNT(*) FROM cpu")).isEqualTo(10);
        assertThat(count("SELECT COUNT(*) FROM product")).isEqualTo(20);
        assertThat(count("SELECT COUNT(*) FROM laptop")).isEqualTo(20);
        assertThat(count("SELECT COUNT(*) FROM product_offer")).isEqualTo(20);
        assertThat(count("SELECT COUNT(*) FROM product WHERE image_key IS NOT NULL")).isEqualTo(20);
        assertThat(count("SELECT COUNT(DISTINCT image_key) FROM product")).isEqualTo(20);
        assertThat(count("SELECT COUNT(*) FROM product_offer WHERE checked_at IS NOT NULL")).isEqualTo(20);
    }

    @Test
    void 생성한_SQL이_검토한_JSON의_제품_사양과_판매_정보를_모두_보존한다() throws IOException {
        JsonNode products = readCatalog("products.json").path("products");
        JsonNode cpus = readCatalog("cpus.json").path("cpus");
        Map<String, JsonNode> productByCode = new HashMap<>();
        Map<String, JsonNode> cpuByKey = new HashMap<>();
        products.forEach(item -> productByCode.put(item.path("product").path("code").asText(), item));
        cpus.forEach(cpu -> cpuByKey.put(cpu.path("cpuKey").asText(), cpu));

        var rows = jdbcTemplate.queryForList("""
            SELECT p.code, p.brand, p.name, p.description, p.image_key, pc.code AS category_code,
                   l.os, l.memory_gb, l.storage_gb, l.weight_g, l.screen_size_inch,
                   c.manufacturer AS cpu_manufacturer, c.name AS cpu_name, c.core_count, c.score,
                   o.name AS offer_name, o.price, o.external_item_id, o.purchase_url,
                   o.status, o.checked_at
            FROM product p
                     JOIN product_category pc ON pc.id = p.product_category_id
                     JOIN laptop l ON l.id = p.id
                     JOIN cpu c ON c.id = l.cpu_id
                     JOIN product_offer o ON o.product_id = p.id
            """);
        assertThat(rows).hasSize(products.size());

        for (Map<String, Object> row : rows) {
            String code = (String) row.get("code");
            JsonNode item = productByCode.get(code);
            assertThat(item).as("제품 코드 %s", code).isNotNull();
            JsonNode cpu = cpuByKey.get(item.path("laptop").path("cpuKey").asText());
            assertThat(cpu).as("CPU: %s", code).isNotNull();
            assertThat(row).as("제품 코드 %s", code)
                .containsExactlyInAnyOrderEntriesOf(expectedRow(item, cpu));
        }
    }

    @Test
    void 보완한_CPU와_원본_가격_확인일의_대표값을_검증한다() {
        String cpuName = jdbcTemplate.queryForObject("""
            SELECT c.name
            FROM product p
                     JOIN laptop l ON l.id = p.id
                     JOIN cpu c ON c.id = l.cpu_id
            WHERE p.code = 'NT760VJT-A72A'
            """, String.class);
        assertThat(cpuName).isEqualTo("Intel Core Ultra 7 355");

        OfferSnapshot offer = jdbcTemplate.queryForObject("""
            SELECT o.price, o.purchase_url, o.checked_at
            FROM product_offer o
                     JOIN product p ON p.id = o.product_id
            WHERE p.code = 'MDH74KH/A'
            """, (rs, rowNum) -> new OfferSnapshot(
            rs.getLong("price"),
            rs.getString("purchase_url"),
            rs.getDate("checked_at").toLocalDate()
        ));
        assertThat(offer).isEqualTo(new OfferSnapshot(
            2080500L,
            "https://www.coupang.com/vp/products/9410641464?itemId=27961674147&vendorItemId=94919713475",
            LocalDate.of(2026, 8, 19)
        ));

        assertThat(count("SELECT COUNT(*) FROM product_offer WHERE checked_at = '2026-08-14'"))
            .isEqualTo(15);
        assertThat(count("SELECT COUNT(*) FROM product_offer WHERE checked_at = '2026-08-19'"))
            .isEqualTo(5);
    }

    @Test
    void 등록된_20개_제품을_API에서_검색하고_가격순으로_정렬한다() throws Exception {
        String path = "/api/product-categories/LAPTOP/products";
        JsonNode all = responseOf(path, "size", "100");
        assertThat(all.path("content")).hasSize(20);
        assertThat(all.path("hasNext").asBoolean()).isFalse();
        for (JsonNode item : all.path("content")) {
            String imageKey = jdbcTemplate.queryForObject(
                "SELECT image_key FROM product WHERE id = ?", String.class, item.path("id").asLong());
            assertThat(item.path("imageUrl").asText()).isEqualTo("https://images.example.test/" + imageKey);
            assertThat(item.has("imageKey")).isFalse();
        }

        JsonNode apple = responseOf(path, "size", "100", "brand", "Apple");
        assertThat(apple.path("content")).hasSize(12);
        for (JsonNode item : apple.path("content")) {
            assertThat(item.path("brand").asText()).isEqualTo("Apple");
        }

        JsonNode zenbook = responseOf(path, "keyword", "Zenbook");
        assertThat(zenbook.path("content")).hasSize(1);
        assertThat(zenbook.path("content").get(0).path("name").asText()).contains("Zenbook");

        List<Long> ascending = prices(responseOf(path, "size", "100", "sort", "PRICE_ASC"));
        List<Long> descending = prices(responseOf(path, "size", "100", "sort", "PRICE_DESC"));
        assertThat(ascending).hasSize(20).isSorted();
        assertThat(descending).hasSize(20).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void 등록된_20개_제품의_상세_API가_이미지_가격_구매_링크_확인일을_반환한다() throws Exception {
        JsonNode products = readCatalog("products.json").path("products");
        for (JsonNode item : products) {
            JsonNode product = item.path("product");
            JsonNode metadata = item.path("metadata");
            JsonNode offer = item.path("offers").get(0);
            Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE code = ?", Long.class, product.path("code").asText());

            JsonNode detail = responseOf("/api/products/" + id);
            assertThat(detail.path("code").asText()).isEqualTo(product.path("code").asText());
            assertThat(detail.path("imageUrl").asText())
                .isEqualTo("https://images.example.test/" + metadata.path("imageKey").asText());
            assertThat(detail.has("imageKey")).isFalse();
            assertThat(detail.path("offers")).hasSize(1);
            JsonNode actualOffer = detail.path("offers").get(0);
            assertThat(actualOffer.path("price").asLong()).isEqualTo(offer.path("price").asLong());
            assertThat(actualOffer.path("purchaseUrl").asText()).isEqualTo(offer.path("purchaseUrl").asText());
            assertThat(actualOffer.path("checkedAt").asText())
                .isEqualTo(metadata.path("priceCheckedAt").asText());
        }
    }

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private Map<String, Object> expectedRow(JsonNode item, JsonNode cpu) {
        JsonNode product = item.path("product");
        JsonNode laptop = item.path("laptop");
        JsonNode offer = item.path("offers").get(0);
        JsonNode metadata = item.path("metadata");
        return Map.ofEntries(
            Map.entry("code", product.path("code").asText()),
            Map.entry("brand", product.path("brand").asText()),
            Map.entry("name", product.path("name").asText()),
            Map.entry("description", product.path("description").asText()),
            Map.entry("image_key", metadata.path("imageKey").asText()),
            Map.entry("category_code", product.path("categoryCode").asText()),
            Map.entry("os", laptop.path("os").asText()),
            Map.entry("memory_gb", laptop.path("memoryGb").asInt()),
            Map.entry("storage_gb", laptop.path("storageGb").asInt()),
            Map.entry("weight_g", laptop.path("weightG").asInt()),
            Map.entry("screen_size_inch", new BigDecimal(laptop.path("screenSizeInch").asText())),
            Map.entry("cpu_manufacturer", cpu.path("manufacturer").asText()),
            Map.entry("cpu_name", cpu.path("name").asText()),
            Map.entry("core_count", cpu.path("coreCount").asInt()),
            Map.entry("score", cpu.path("score").asInt()),
            Map.entry("offer_name", offer.path("name").asText()),
            Map.entry("price", offer.path("price").asLong()),
            Map.entry("external_item_id", offer.path("externalItemId").asText()),
            Map.entry("purchase_url", offer.path("purchaseUrl").asText()),
            Map.entry("status", offer.path("status").asText()),
            Map.entry("checked_at", Date.valueOf(metadata.path("priceCheckedAt").asText()))
        );
    }

    private JsonNode readCatalog(String name) throws IOException {
        return new ObjectMapper().readTree(Path.of("data", "product-catalog", name).toFile());
    }

    private JsonNode responseOf(String path, String... params) throws Exception {
        var request = get(path);
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        String body = mockMvc.perform(request).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(body);
    }

    private List<Long> prices(JsonNode response) {
        return StreamSupport.stream(response.path("content").spliterator(), false)
            .map(item -> item.path("minPrice").asLong())
            .toList();
    }

    private record OfferSnapshot(long price, String purchaseUrl, LocalDate checkedAt) {
    }
}
