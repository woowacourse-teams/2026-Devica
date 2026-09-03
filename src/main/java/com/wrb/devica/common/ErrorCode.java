package com.wrb.devica.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    PRODUCT_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 제품 종류입니다."),
    USAGE_PURPOSE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용 목적입니다."),
    RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "권장 사양이 존재하지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
