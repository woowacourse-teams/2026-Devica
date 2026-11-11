package com.wrb.devica.product.infra.coupang;

import lombok.Getter;

@Getter
public class CoupangApiException extends RuntimeException {

    private final Reason reason;
    private final String responseCode;

    public CoupangApiException(Reason reason) {
        this(reason, null);
    }

    public CoupangApiException(Reason reason, String responseCode) {
        // 외부 응답 본문이나 인증 헤더를 예외 메시지에 포함하지 않는다.
        super(reason.name());
        this.reason = reason;
        this.responseCode = responseCode;
    }

    public enum Reason {
        AUTHENTICATION_FAILED,
        RATE_LIMITED,
        HTTP_ERROR,
        CONNECTION_FAILED,
        API_REJECTED,
        INVALID_RESPONSE
    }
}
