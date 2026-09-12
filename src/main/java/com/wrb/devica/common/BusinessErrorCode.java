package com.wrb.devica.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 도메인 규칙을 어겼을 때 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum BusinessErrorCode implements ErrorCode {

    PRODUCT_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 제품 종류입니다."),
    LAPTOP_NOT_FOUND(HttpStatus.NOT_FOUND, "조회할 수 없는 노트북입니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
