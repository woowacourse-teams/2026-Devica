package com.wrb.devica.product;

/**
 * 제품별 최저가. 목록 조회에서 노트북을 먼저 고른 뒤, 그 제품들의 최저가만 한 번에 가져온다.
 */
public record ProductMinPrice(Long productId, long minPrice) {
}
