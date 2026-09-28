package com.wrb.devica.product.service;

import static com.wrb.devica.fixture.CpuFixture.cpu;
import static com.wrb.devica.fixture.LaptopFixture.laptop;
import static com.wrb.devica.fixture.ProductOfferFixture.offer;
import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.category.domain.ProductCategory;
import com.wrb.devica.category.domain.ProductCategoryCode;
import com.wrb.devica.category.repository.ProductCategoryRepository;
import com.wrb.devica.common.JpaSliceTest;
import com.wrb.devica.product.domain.Laptop;
import com.wrb.devica.product.domain.OfferStatus;
import com.wrb.devica.product.domain.ProductOffer;
import com.wrb.devica.product.repository.CpuRepository;
import com.wrb.devica.product.repository.LaptopRepository;
import com.wrb.devica.product.repository.ProductOfferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@JpaSliceTest
@Import(ProductOfferService.class)
class ProductOfferServiceTest {

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private CpuRepository cpuRepository;

    @Autowired
    private LaptopRepository laptopRepository;

    @Autowired
    private ProductOfferRepository productOfferRepository;

    @Autowired
    private ProductOfferService productOfferService;

    @Test
    void 판매_중인_판매처만_가격_오름차순으로_반환한다() {
        // given
        Laptop laptop = laptopRepository.save(laptop()
            .category(productCategoryRepository.save(ProductCategory.from(ProductCategoryCode.LAPTOP)))
            .cpu(cpuRepository.save(cpu().build()))
            .build());

        productOfferRepository.save(offer().product(laptop).price(2_990_000L).status(OfferStatus.ON_SALE).build());
        productOfferRepository.save(offer().product(laptop).price(2_850_000L).status(OfferStatus.ON_SALE).build());
        productOfferRepository.save(offer().product(laptop).price(2_500_000L).status(OfferStatus.SOLD_OUT).build());

        // when & then
        assertThat(productOfferService.findOnSaleOffers(laptop.getId()))
            .extracting(ProductOffer::getPrice)
            .containsExactly(2_850_000L, 2_990_000L);
    }
}
