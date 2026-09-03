package com.wrb.devica.common;

public record ErrorResponse(String message, String code) {

    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getMessage(), errorCode.getCode());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(message, errorCode.getCode());
    }
}
