package com.wrb.devica.product;

/**
 * 사양의 각 항목을 제품 종류와 무관한 형태로 일반화한다.
 * 예: code=MEMORY, displayName=메모리, value=24, displayValue=24GB
 */
public record SpecItem(String code, String displayName, String value, String displayValue) {
}
