package com.wrb.devica.product.domain;

// 판매처를 식별하는 코드. 화면에 보이는 판매처 이름은 ProductOffer.name 이 따로 가진다.
// 값을 추가하면 product_offer.seller 를 채우는 마이그레이션도 함께 작성한다.
public enum Seller {

    COUPANG,
    APPLE_STORE,
    STUDENT_STORE,
    DAANGN
}
