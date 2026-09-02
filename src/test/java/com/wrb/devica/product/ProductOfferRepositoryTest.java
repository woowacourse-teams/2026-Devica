package com.wrb.devica.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProductOfferRepositoryTest extends LaptopJpaTestSupport {

    @Autowired
    private ProductOfferRepository productOfferRepository;

    @Test
    void 판매_중인_오퍼를_조회하면_가격_오름차순으로_반환한다() {
        // given
        Laptop laptop = saveLaptopWithoutOffer("노트북", Os.WINDOWS, 10000, 16, 512);
        saveOffer(laptop, 2_990_000L, OfferStatus.ON_SALE);
        saveOffer(laptop, 2_850_000L, OfferStatus.ON_SALE);
        saveOffer(laptop, 2_910_000L, OfferStatus.ON_SALE);

        // when
        List<ProductOffer> found = findOnSaleOffers(laptop);

        // then
        assertThat(found).extracting(ProductOffer::getPrice)
            .containsExactly(2_850_000L, 2_910_000L, 2_990_000L);
    }

    @Test
    void 판매_중이_아닌_오퍼는_조회되지_않는다() {
        // given
        Laptop laptop = saveLaptopWithoutOffer("노트북", Os.WINDOWS, 10000, 16, 512);
        saveOffer(laptop, 900_000L, OfferStatus.SOLD_OUT);
        saveOffer(laptop, 800_000L, OfferStatus.DISCONTINUED);
        saveOffer(laptop, 2_000_000L, OfferStatus.ON_SALE);

        // when
        List<ProductOffer> found = findOnSaleOffers(laptop);

        // then
        assertThat(found).extracting(ProductOffer::getPrice).containsExactly(2_000_000L);
    }

    @Test
    void 다른_제품의_오퍼는_조회되지_않는다() {
        // given
        Laptop laptop = saveLaptopWithoutOffer("대상", Os.WINDOWS, 10000, 16, 512);
        saveOffer(laptop, 1_000_000L, OfferStatus.ON_SALE);
        Laptop other = saveLaptopWithoutOffer("다른것", Os.WINDOWS, 10000, 16, 512);
        saveOffer(other, 500_000L, OfferStatus.ON_SALE);

        // when
        List<ProductOffer> found = findOnSaleOffers(laptop);

        // then
        assertThat(found).extracting(ProductOffer::getPrice).containsExactly(1_000_000L);
    }

    @Test
    void 판매_중인_오퍼가_없으면_빈_결과를_반환한다() {
        // given
        Laptop laptop = saveLaptopWithoutOffer("노트북", Os.WINDOWS, 10000, 16, 512);
        saveOffer(laptop, 900_000L, OfferStatus.SOLD_OUT);

        // when
        List<ProductOffer> found = findOnSaleOffers(laptop);

        // then
        assertThat(found).isEmpty();
    }

    private List<ProductOffer> findOnSaleOffers(Laptop laptop) {
        flushAndClear();
        return productOfferRepository.findAllByProductIdAndStatusOrderByPriceAsc(
            laptop.getId(), OfferStatus.ON_SALE);
    }
}
