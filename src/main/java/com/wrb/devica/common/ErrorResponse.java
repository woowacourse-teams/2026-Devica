package com.wrb.devica.common;

public record ErrorResponse(String message, String code) {

    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getMessage(), errorCode.name());
    }
}
