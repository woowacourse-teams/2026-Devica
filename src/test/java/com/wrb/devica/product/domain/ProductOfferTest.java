package com.wrb.devica.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProductOfferTest {

    private static final long PREVIOUS_PRICE = 1_000_000L;
    private static final LocalDate PREVIOUS_CHECKED_AT = LocalDate.of(2026, 10, 29);
    private static final LocalDate CHECKED_AT = PREVIOUS_CHECKED_AT.plusDays(1);

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void 가격이_0_이하면_거절하고_기존_정보를_유지한다(long price) {
        // given
        ProductOffer offer = offer(OfferStatus.HIDDEN);

        // when & then
        assertThatThrownBy(() -> offer.refreshPrice(price, CHECKED_AT)).isInstanceOf(IllegalArgumentException.class);
        assertThat(offer).extracting(ProductOffer::getPrice, ProductOffer::getCheckedAt, ProductOffer::getStatus)
            .containsExactly(PREVIOUS_PRICE, PREVIOUS_CHECKED_AT, OfferStatus.HIDDEN);
    }

    @Test
    void 확인_날짜가_없으면_거절하고_기존_정보를_유지한다() {
        // given
        ProductOffer offer = offer(OfferStatus.HIDDEN);

        // when & then
        assertThatThrownBy(() -> offer.refreshPrice(900_000L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(offer).extracting(ProductOffer::getPrice, ProductOffer::getCheckedAt, ProductOffer::getStatus)
            .containsExactly(PREVIOUS_PRICE, PREVIOUS_CHECKED_AT, OfferStatus.HIDDEN);
    }

    @ParameterizedTest
    @ValueSource(longs = {900_000L, PREVIOUS_PRICE})
    void 유효한_가격과_확인_날짜를_반영하고_숨김을_복구한다(long price) {
        // given
        ProductOffer offer = offer(OfferStatus.HIDDEN);

        // when
        offer.refreshPrice(price, CHECKED_AT);

        // then
        assertThat(offer).extracting(ProductOffer::getPrice, ProductOffer::getCheckedAt, ProductOffer::getStatus)
            .containsExactly(price, CHECKED_AT, OfferStatus.ON_SALE);
    }

    @Test
    void 숨기면_가격과_확인_날짜를_유지한다() {
        // given
        ProductOffer offer = offer(OfferStatus.ON_SALE);

        // when
        offer.hide();

        // then
        assertThat(offer).extracting(ProductOffer::getPrice, ProductOffer::getCheckedAt, ProductOffer::getStatus)
            .containsExactly(PREVIOUS_PRICE, PREVIOUS_CHECKED_AT, OfferStatus.HIDDEN);
    }

    private ProductOffer offer(OfferStatus status) {
        return ProductOffer.builder()
            .productId(1L)
            .name("쿠팡")
            .price(PREVIOUS_PRICE)
            .purchaseUrl("https://www.coupang.com/vp/products/123?vendorItemId=456")
            .status(status)
            .checkedAt(PREVIOUS_CHECKED_AT)
            .build();
    }
}
