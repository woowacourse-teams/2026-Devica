package com.wrb.devica.product.infra.coupang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class CoupangHmacSignerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-22T01:02:03Z"), ZoneId.of("Asia/Seoul"));
    private final CoupangHmacSigner signer = new CoupangHmacSigner("test-access", "test-secret", clock);

    @Test
    void UTC_시각과_실제로_전송할_인코딩된_쿼리로_서명한다() {
        URI uri = URI.create("https://api-gateway.coupang.com"
            + "/v2/providers/affiliate_open_api/apis/openapi/v1/products/search"
            + "?keyword=%EB%85%B8%ED%8A%B8%EB%B6%81+%2B+16GB&limit=10");

        // Python hmac으로 별도 계산한 고정 벡터. 디코딩한 검색어로 서명하면 이 값과 달라진다.
        assertThat(signer.sign("GET", uri)).isEqualTo("CEA algorithm=HmacSHA256, access-key=test-access, "
            + "signed-date=261022T010203Z, signature=1941472876177de98011884a3989c860650e52ade6c21a919a4d5add284c9dc9");
    }

    @Test
    void 쿼리가_없으면_빈_문자열로_서명한다() {
        URI uri = URI.create("https://api-gateway.coupang.com"
            + "/v2/providers/affiliate_open_api/apis/openapi/v1/products/search");

        assertThat(signer.sign("GET", uri))
            .endsWith("signature=5dd7beb1fec9aba2807139ef3d66f25faa5a37c8d30d92d591c61133d8e16129");
    }

    @Test
    void 키가_비어_있으면_요청_전에_실패한다() {
        assertThatThrownBy(() -> new CoupangHmacSigner("", "secret-value", clock))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageNotContaining("secret-value");
    }
}
