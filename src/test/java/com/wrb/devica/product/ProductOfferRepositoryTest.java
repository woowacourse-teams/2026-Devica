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
        Laptop laptop = laptop().name("노트북").save();

        offer().product(laptop).price(2_990_000L).status(OfferStatus.ON_SALE).save();
        offer().product(laptop).price(2_850_000L).status(OfferStatus.ON_SALE).save();
        offer().product(laptop).price(2_910_000L).status(OfferStatus.ON_SALE).save();

        // when
        List<ProductOffer> found = findOnSaleOffers(laptop);

        // then
        assertThat(found).extracting(ProductOffer::getPrice)
            .containsExactly(2_850_000L, 2_910_000L, 2_990_000L);
    }

    @Test
    void 판매_중이_아닌_오퍼는_조회되지_않는다() {
        // given
        Laptop laptop = laptop().name("노트북").save();

        offer().product(laptop).price(900_000L).status(OfferStatus.SOLD_OUT).save();
        offer().product(laptop).price(800_000L).status(OfferStatus.DISCONTINUED).save();
        offer().product(laptop).price(2_000_000L).status(OfferStatus.ON_SALE).save();

        // when
        List<ProductOffer> found = findOnSaleOffers(laptop);

        // then
        assertThat(found).extracting(ProductOffer::getPrice).containsExactly(2_000_000L);
    }

    @Test
    void 다른_제품의_오퍼는_조회되지_않는다() {
        // given
        Laptop laptop = laptop().name("대상").save();
        offer().product(laptop).price(1_000_000L).status(OfferStatus.ON_SALE).save();

        Laptop other = laptop().name("다른것").save();
        offer().product(other).price(500_000L).status(OfferStatus.ON_SALE).save();

        // when
        List<ProductOffer> found = findOnSaleOffers(laptop);

        // then
        assertThat(found).extracting(ProductOffer::getPrice).containsExactly(1_000_000L);
    }

    @Test
    void 판매_중인_오퍼가_없으면_빈_결과를_반환한다() {
        // given
        Laptop laptop = laptop().name("노트북").save();
        offer().product(laptop).price(900_000L).status(OfferStatus.SOLD_OUT).save();

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
