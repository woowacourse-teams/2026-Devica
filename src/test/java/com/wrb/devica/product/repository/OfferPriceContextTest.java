package com.wrb.devica.product.repository;

import static com.wrb.devica.fixture.CpuFixture.cpu;
import static com.wrb.devica.fixture.LaptopFixture.laptop;
import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.category.domain.ProductCategory;
import com.wrb.devica.category.domain.ProductCategoryCode;
import com.wrb.devica.category.repository.ProductCategoryRepository;
import com.wrb.devica.common.JpaSliceTest;
import com.wrb.devica.product.domain.OfferLinkType;
import com.wrb.devica.product.domain.OfferPriceBasis;
import com.wrb.devica.product.domain.OfferStatus;
import com.wrb.devica.product.domain.ProductOffer;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@JpaSliceTest
class OfferPriceContextTest {
    @Autowired private ProductOfferRepository offers;
    @Autowired private LaptopRepository laptops;
    @Autowired private CpuRepository cpus;
    @Autowired private ProductCategoryRepository categories;
    @Autowired private EntityManager em;

    @Test
    void 조건부_판매가와_관측_최저가의_근거를_저장하고_다시_읽는다() {
        // given
        var laptop = laptops.save(laptop().cpu(cpus.save(cpu().build()))
            .category(categories.save(ProductCategory.from(ProductCategoryCode.LAPTOP))).build());
        var student = offers.save(ProductOffer.builder().productId(laptop.getId()).name("학생복지스토어")
            .price(1_000_000L).purchaseUrl("https://example.test/student").status(OfferStatus.ON_SALE)
            .priceCondition("학생 인증 필요").checkedAt(LocalDate.of(2026, 10, 30)).build());
        var observed = offers.save(ProductOffer.builder().productId(laptop.getId()).name("당근")
            .price(900_000L).purchaseUrl("https://example.test/search").status(OfferStatus.ON_SALE)
            .priceCondition("판교 · 미개봉 · 확인 매물 중 최저가").checkedAt(LocalDate.of(2026, 10, 30))
            .priceBasis(OfferPriceBasis.OBSERVED_LOWEST).linkType(OfferLinkType.SEARCH).build());

        // when
        em.flush();
        em.clear();

        // then
        assertThat(offers.findById(student.getId()).orElseThrow())
            .extracting(ProductOffer::getPriceCondition, ProductOffer::getPriceBasis, ProductOffer::getLinkType)
            .containsExactly("학생 인증 필요", OfferPriceBasis.SELLER_PRICE, OfferLinkType.PURCHASE);
        assertThat(offers.findById(observed.getId()).orElseThrow())
            .extracting(ProductOffer::getPriceCondition, ProductOffer::getPriceBasis, ProductOffer::getLinkType,
                ProductOffer::getCheckedAt, ProductOffer::getPurchaseUrl)
            .containsExactly("판교 · 미개봉 · 확인 매물 중 최저가", OfferPriceBasis.OBSERVED_LOWEST,
                OfferLinkType.SEARCH, LocalDate.of(2026, 10, 30), "https://example.test/search");
    }
}
