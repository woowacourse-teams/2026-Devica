package com.wrb.devica.product.infra.coupang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class CoupangPriceProbeTest {

    private static final String SEARCH_URL = "https://api-gateway.coupang.com"
        + "/v2/providers/affiliate_open_api/apis/openapi/v1/products/search?keyword=laptop&limit=10";
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-22T01:02:03Z"), ZoneOffset.UTC);
    private final CoupangPriceProbe.Target target = new CoupangPriceProbe.Target("MODEL-A", "laptop",
        "https://www.coupang.com/vp/products/123?itemId=456&vendorItemId=789", "RAM 16GB / SSD 512GB / Windows");
    private MockRestServiceServer server;
    private CoupangProductSearchClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CoupangProductSearchClient(builder.build(),
            new CoupangHmacSigner("test-access", "test-secret", clock));
    }

    @Test
    void ID가_일치해도_제품_사양과_가격의_검증이_완료된_것으로_표시하지_않는다() {
        // given
        server.expect(requestTo(SEARCH_URL)).andRespond(withSuccess("""
            {"rCode":"0","data":{"productData":[{
              "productId":999,"productName":"노트북","productPrice":2000000,
              "productUrl":"https://link.coupang.com/re/AFFSDP?pageKey=123&itemId=456&vendorItemId=789"
            }]}}
            """, MediaType.APPLICATION_JSON));

        // when
        var result = CoupangPriceProbe.inspect(target, client, clock);

        // then
        assertThat(result.outcome()).isEqualTo("RETRIEVED");
        assertThat(result.candidates().getFirst().response().productId()).isEqualTo("999");
        assertThat(result.candidates().getFirst().urlIdentifiers().pageId()).isEqualTo("123");
        assertThat(result.candidates().getFirst().optionMatch()).isEqualTo(
            CoupangProductUrl.OptionMatch.MATCHED_OPTION_IDS);
        assertThat(result.candidates().getFirst().priceCheck()).isEqualTo("REQUIRES_PRICE_VERIFICATION");
        String report = JsonMapper.builder().build().writeValueAsString(
            new CoupangPriceProbe.Report("MANUAL_VERIFICATION_REQUIRED", false, List.of(result)));
        assertThat(report).contains("2026-10-22T01:02:03Z", "MANUAL_VERIFICATION_REQUIRED")
            .doesNotContain("test-access", "test-secret", "Authorization");
        server.verify();
    }

    @Test
    void 상품명이_같아도_옵션_ID가_없거나_가격이_유효하지_않으면_확인_대상으로_남긴다() {
        // given
        server.expect(requestTo(SEARCH_URL)).andRespond(withSuccess("""
            {"rCode":"0","data":{"productData":[{
              "productId":123,"productName":"MODEL-A RAM 16GB SSD 512GB Windows","productPrice":0,
              "productUrl":"https://www.coupang.com/vp/products/123"
            }]}}
            """, MediaType.APPLICATION_JSON));

        // when
        var result = CoupangPriceProbe.inspect(target, client, clock);

        // then
        assertThat(result.candidates().getFirst().optionMatch())
            .isEqualTo(CoupangProductUrl.OptionMatch.INSUFFICIENT_IDENTIFIERS);
        assertThat(result.candidates().getFirst().priceCheck()).isEqualTo("MISSING_OR_INVALID_PRICE");
        server.verify();
    }

    @Test
    void 호출_제한은_품절이나_빈_검색_결과로_기록하지_않는다() {
        // given
        server.expect(requestTo(SEARCH_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // when
        var result = CoupangPriceProbe.inspect(target, client, clock);

        // then
        assertThat(result.outcome()).isEqualTo("API_FAILURE");
        assertThat(result.failureReason()).isEqualTo("RATE_LIMITED");
        assertThat(result.candidates()).isEmpty();
        server.verify();
    }
}
