package com.wrb.devica.product;

/**
 * 사양 한 항목의 코드와 원본 값. 예: code=MEMORY, value=24
 * 표시 형태(메모리, 24GB)는 표현 계층이 만든다.
 */
public record SpecValue(String code, String value) {
}
