package com.wrb.devica.product.infra.coupang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CoupangProductSearchClientTest {

    private static final String SEARCH_URL = "https://api-gateway.coupang.com"
        + "/v2/providers/affiliate_open_api/apis/openapi/v1/products/search";

    private MockRestServiceServer server;
    private CoupangProductSearchClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        Clock clock = Clock.fixed(Instant.parse("2026-10-22T01:02:03Z"), ZoneOffset.UTC);
        client = new CoupangProductSearchClient(builder.build(),
            new CoupangHmacSigner("test-access", "test-secret", clock));
    }

    @Test
    void 한글과_예약문자를_인코딩한_URI와_동일한_서명을_전송한다() {
        // given
        server.expect(requestTo(SEARCH_URL + "?keyword=%EB%85%B8%ED%8A%B8%EB%B6%81+%2B+16GB&limit=10"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "CEA algorithm=HmacSHA256, access-key=test-access, "
                + "signed-date=261022T010203Z, signature=1941472876177de98011884a3989c860650e52ade6c21a919a4d5add284c9dc9"))
            .andRespond(withSuccess("""
                {"rCode":"0","rMessage":"","data":{"landingUrl":"ignored","productData":[{
                  "productId":9410641464,"productName":"노트북 16GB","productPrice":2080500,
                  "productUrl":"https://link.coupang.com/re/AFFSDP?pageKey=12&itemId=34&vendorItemId=56",
                  "isRocket":true,"additionalField":"ignored"
                }]}}
                """, MediaType.APPLICATION_JSON));

        // when
        var results = client.search("노트북 + 16GB");

        // then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().productId()).isEqualTo("9410641464");
        assertThat(results.getFirst().productPrice()).isEqualTo(2080500L);
        assertThat(results.getFirst().productUrl()).contains("pageKey=12");
        server.verify();
    }

    @Test
    void 검색_결과가_없는_정상_응답은_오류와_구분한다() {
        // given
        server.expect(requestTo(SEARCH_URL + "?keyword=laptop&limit=10"))
            .andRespond(withSuccess("{\"rCode\":\"0\",\"data\":{\"productData\":[]}}", MediaType.APPLICATION_JSON));

        // when & then
        assertThat(client.search("laptop")).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"302, HTTP_ERROR", "401, AUTHENTICATION_FAILED", "403, AUTHENTICATION_FAILED",
        "429, RATE_LIMITED", "500, HTTP_ERROR"})
    void HTTP_오류는_응답_원문을_노출하지_않고_구분한다(int status, CoupangApiException.Reason reason) {
        // given
        server.expect(requestTo(SEARCH_URL + "?keyword=laptop&limit=10"))
            .andRespond(withStatus(HttpStatusCode.valueOf(status)).body("sensitive-server-body"));

        // when & then
        assertThatThrownBy(() -> client.search("laptop"))
            .isInstanceOfSatisfying(CoupangApiException.class,
                exception -> {
                    assertThat(exception.getReason()).isEqualTo(reason);
                    assertThat(exception.getResponseCode()).isNull();
                })
            .hasMessage(reason.name())
            .hasNoCause();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "403", "429", "UNKNOWN_CODE", ""})
    void HTTP_성공이어도_rCode가_실패이면_원래_코드와_함께_거절로_처리한다(String responseCode) {
        // given
        server.expect(requestTo(SEARCH_URL + "?keyword=laptop&limit=10"))
            .andRespond(
                withSuccess("{\"rCode\":\"%s\",\"rMessage\":\"sensitive-server-body\"}".formatted(responseCode),
                    MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.search("laptop"))
            .isInstanceOfSatisfying(CoupangApiException.class, exception -> {
                assertThat(exception.getReason()).isEqualTo(CoupangApiException.Reason.API_REJECTED);
                assertThat(exception.getResponseCode()).isEqualTo(responseCode);
            })
            .hasMessage("API_REJECTED").hasNoCause();
        server.verify();
    }

    @Test
    void 응답_코드_원문은_예외에_보존하되_예외_문자열에는_노출하지_않는다() {
        // given
        String responseCode = "403\r\n\t" + "x".repeat(100);
        String encodedCode = "403\\r\\n\\t" + "x".repeat(100);
        server.expect(requestTo(SEARCH_URL + "?keyword=laptop&limit=10"))
            .andRespond(withSuccess("{\"rCode\":\"%s\",\"rMessage\":\"sensitive-server-body\"}"
                .formatted(encodedCode), MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.search("laptop"))
            .isInstanceOfSatisfying(CoupangApiException.class, exception -> {
                assertThat(exception.getResponseCode()).isEqualTo(responseCode);
                assertThat(exception.toString()).doesNotContain(responseCode, "sensitive-server-body");
            })
            .hasMessage("API_REJECTED").hasNoCause();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-json", "{}", "{\"rCode\":null}", "{\"rCode\":\"0\"}", "{\"rCode\":\"0\",\"data\":{}}",
        "{\"rCode\":\"0\",\"data\":{\"productData\":[null]}}"})
    void 누락되거나_깨진_응답을_빈_검색_결과로_취급하지_않는다(String response) {
        // given
        server.expect(requestTo(SEARCH_URL + "?keyword=laptop&limit=10"))
            .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.search("laptop"))
            .isInstanceOfSatisfying(CoupangApiException.class,
                exception -> assertThat(exception.getResponseCode()).isNull())
            .hasMessage("INVALID_RESPONSE");
        server.verify();
    }

    @Test
    void 타임아웃을_구분하고_즉시_반복_요청하지_않는다() {
        // given
        server.expect(requestTo(SEARCH_URL + "?keyword=laptop&limit=10"))
            .andRespond(withException(new SocketTimeoutException("timeout")));

        // when & then
        assertThatThrownBy(() -> client.search("laptop"))
            .isInstanceOfSatisfying(CoupangApiException.class,
                exception -> assertThat(exception.getResponseCode()).isNull())
            .hasMessage("CONNECTION_FAILED");
        server.verify();
    }

    @Test
    void 빈_검색어는_외부에_전송하지_않는다() {
        // when & then
        assertThatThrownBy(() -> client.search(" ")).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }
}
