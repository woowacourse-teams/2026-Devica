package com.wrb.devica.common;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * 요청 자체가 잘못되어 Spring 이 상태를 정한 경우에 쓴다. 상태 하나에 하나만 둔다.
 * 도메인 규칙 위반은 {@link BusinessErrorCode} 를 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 형식입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했습니다.");

    private final HttpStatus status;
    private final String message;

    public static CommonErrorCode from(HttpStatusCode status) {
        return Arrays.stream(values())
            .filter(errorCode -> errorCode.status.value() == status.value())
            .findFirst()
            .orElse(INTERNAL_SERVER_ERROR);
    }

    @Override
    public String getCode() {
        return name();
    }
}
