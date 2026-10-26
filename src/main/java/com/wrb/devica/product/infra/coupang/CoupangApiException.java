package com.wrb.devica.product.infra.coupang;

public class CoupangApiException extends RuntimeException {

    private final Reason reason;

    public CoupangApiException(Reason reason) {
        // 외부 응답 본문이나 인증 헤더를 예외 메시지에 포함하지 않는다.
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
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
