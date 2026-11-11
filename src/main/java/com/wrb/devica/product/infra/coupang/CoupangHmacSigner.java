package com.wrb.devica.product.infra.coupang;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class CoupangHmacSigner {

    private static final DateTimeFormatter SIGNED_DATE = DateTimeFormatter
        .ofPattern("yyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC);
    private static final String ALGORITHM = "HmacSHA256";

    private final String accessKey;
    private final String secretKey;
    private final Clock clock;

    public CoupangHmacSigner(String accessKey, String secretKey, Clock clock) {
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("쿠팡 Access Key와 Secret Key를 설정해야 합니다.");
        }
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.clock = clock;
    }

    public String sign(String method, URI uri) {
        String signedDate = SIGNED_DATE.format(clock.instant());
        String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
        String message = signedDate + method + uri.getRawPath() + query;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            String signature = HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
            return "CEA algorithm=" + ALGORITHM + ", access-key=" + accessKey
                + ", signed-date=" + signedDate + ", signature=" + signature;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("쿠팡 요청 서명을 생성하지 못했습니다.", exception);
        }
    }
}
