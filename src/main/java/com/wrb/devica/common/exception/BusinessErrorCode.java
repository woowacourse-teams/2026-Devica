package com.wrb.devica.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * * 도메인 규칙을 어겼을 때 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum BusinessErrorCode implements ErrorCode {

    PRODUCT_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 제품 종류입니다."),
    USAGE_PURPOSE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용 목적입니다."),
    LAPTOP_NOT_FOUND(HttpStatus.NOT_FOUND, "조회할 수 없는 노트북입니다."),
    QUESTION_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 질문입니다."),
    ANSWER_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "질문에 없는 선택지이거나 함께 고를 수 없는 선택지입니다."),
    FAQ_NOT_FOUND(HttpStatus.NOT_FOUND, "조회할 수 없는 FAQ입니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
