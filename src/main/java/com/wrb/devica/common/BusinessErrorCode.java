package com.wrb.devica.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 도메인 규칙을 어겼을 때 쓴다. BusinessException 이 직접 들고 다니므로
 * 상태로 되짚을 일이 없고, 코드가 늘어나도 CommonErrorCode 에 영향을 주지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum BusinessErrorCode implements ErrorCode {

    PRODUCT_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 제품 종류입니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
