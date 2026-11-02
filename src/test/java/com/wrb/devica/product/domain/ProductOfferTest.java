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

    @Test
    void 기존_입력과_쿠팡_갱신은_일반_판매가의_의미를_유지한다() {
        ProductOffer offer = offer(OfferStatus.ON_SALE);
        offer.refreshPrice(900_000L, CHECKED_AT);
        assertThat(offer).extracting(ProductOffer::getPriceCondition, ProductOffer::getPriceBasis,
                ProductOffer::getLinkType)
            .containsExactly("일반 판매가", OfferPriceBasis.SELLER_PRICE, OfferLinkType.PURCHASE);
    }

    @Test
    void 관측_가격은_조건과_검색_링크를_보존한다() {
        ProductOffer offer = observed().build();
        offer.hide();
        offer.refreshPrice(800_000L, CHECKED_AT);
        assertThat(offer).extracting(ProductOffer::getPriceCondition, ProductOffer::getPriceBasis,
                ProductOffer::getLinkType, ProductOffer::getExternalItemId)
            .containsExactly("판교 · 미개봉 · 확인 매물 중 최저가", OfferPriceBasis.OBSERVED_LOWEST,
                OfferLinkType.SEARCH, null);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void 미확인_가격을_0원_이하로_생성할_수_없다(long price) {
        assertThatThrownBy(() -> observed().price(price).build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 관측_범위와_확인일이_없거나_검색을_상품_ID로_등록하면_거절한다() {
        assertThatThrownBy(() -> observed().priceCondition(null).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> observed().checkedAt(null).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> observed().externalItemId("search-query").build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> observed().priceBasis(OfferPriceBasis.SELLER_PRICE).build())
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> observed().priceCondition(" ").build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> observed().priceCondition("가".repeat(256)).build()).isInstanceOf(IllegalArgumentException.class);
    }

    private ProductOffer.ProductOfferBuilder observed() {
        return ProductOffer.builder().productId(1L).name("당근").price(PREVIOUS_PRICE)
            .purchaseUrl("https://example.test/search?q=laptop").status(OfferStatus.ON_SALE)
            .priceCondition("판교 · 미개봉 · 확인 매물 중 최저가")
            .priceBasis(OfferPriceBasis.OBSERVED_LOWEST).linkType(OfferLinkType.SEARCH).checkedAt(CHECKED_AT);
    }
}
