package com.wrb.devica.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.wrb.devica.common.BusinessErrorCode;
import com.wrb.devica.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Slice;

@Import(LaptopService.class)
class LaptopServiceTest extends LaptopJpaTestSupport {

    @Autowired
    private LaptopService laptopService;

    @Test
    void 오퍼가_여러_개일_때_조회하면_가장_낮은_가격을_반환한다() {
        // given
        Laptop laptop = saveLaptopWithoutOffer("노트북", Os.WINDOWS, 10000, 16, 512);
        saveOffer(laptop, 2_500_000L, OfferStatus.ON_SALE);
        saveOffer(laptop, 1_900_000L, OfferStatus.ON_SALE);
        saveOffer(laptop, 2_100_000L, OfferStatus.ON_SALE);

        // when
        Slice<LaptopSummaryResponse> found = findLaptops();

        // then
        assertThat(found.getContent()).singleElement()
            .extracting(LaptopSummaryResponse::minPrice)
            .isEqualTo(1_900_000L);
    }

    @Test
    void 판매_중이_아닌_오퍼가_더_쌀_때_조회하면_최저가에_포함하지_않는다() {
        // given
        Laptop laptop = saveLaptopWithoutOffer("노트북", Os.WINDOWS, 10000, 16, 512);
        saveOffer(laptop, 900_000L, OfferStatus.SOLD_OUT);
        saveOffer(laptop, 800_000L, OfferStatus.DISCONTINUED);
        saveOffer(laptop, 2_000_000L, OfferStatus.ON_SALE);

        // when
        Slice<LaptopSummaryResponse> found = findLaptops();

        // then
        assertThat(found.getContent()).singleElement()
            .extracting(LaptopSummaryResponse::minPrice)
            .isEqualTo(2_000_000L);
    }

    @Test
    void 노트북이_여러_대일_때_조회하면_각자의_최저가를_반환한다() {
        // given
        Laptop first = saveLaptopWithoutOffer("첫번째", Os.WINDOWS, 10000, 16, 512);
        saveOffer(first, 1_000_000L, OfferStatus.ON_SALE);
        saveOffer(first, 1_200_000L, OfferStatus.ON_SALE);

        Laptop second = saveLaptopWithoutOffer("두번째", Os.WINDOWS, 10000, 32, 1024);
        saveOffer(second, 3_000_000L, OfferStatus.ON_SALE);
        saveOffer(second, 2_800_000L, OfferStatus.ON_SALE);

        // when
        Slice<LaptopSummaryResponse> found = findLaptops();

        // then
        assertThat(found.getContent())
            .extracting(LaptopSummaryResponse::name, LaptopSummaryResponse::minPrice)
            .containsExactly(
                tuple("첫번째", 1_000_000L),
                tuple("두번째", 2_800_000L)
            );
    }

    @Test
    void 조회하면_노트북과_cpu_정보를_응답에_담는다() {
        // given
        Laptop laptop = saveLaptopWithoutOffer("gram Pro 16", Os.WINDOWS, 10000, 32, 1024);
        saveOffer(laptop, 2_850_000L, OfferStatus.ON_SALE);

        // when
        LaptopSummaryResponse response = findLaptops().getContent().getFirst();

        // then
        assertThat(response.id()).isEqualTo(laptop.getId());
        assertThat(response.brand()).isEqualTo(laptop.getBrand());
        assertThat(response.name()).isEqualTo(laptop.getName());
        assertThat(response.os()).isEqualTo(Os.WINDOWS);
        assertThat(response.cpuName()).isEqualTo(laptop.getCpu().getName());
        assertThat(response.cpuCoreCount()).isEqualTo(laptop.getCpu().getCoreCount());
        assertThat(response.memoryGb()).isEqualTo(laptop.getMemoryGb());
        assertThat(response.storageGb()).isEqualTo(laptop.getStorageGb());
        assertThat(response.screenSizeInch()).isEqualByComparingTo(laptop.getScreenSizeInch());
    }

    @Test
    void 상세를_조회하면_판매처를_가격_오름차순으로_담는다() {
        // given
        Laptop laptop = saveLaptopWithoutOffer("gram Pro 16", Os.WINDOWS, 10000, 32, 1024);
        saveOffer(laptop, 2_990_000L, OfferStatus.ON_SALE);
        saveOffer(laptop, 2_850_000L, OfferStatus.ON_SALE);
        saveOffer(laptop, 2_500_000L, OfferStatus.SOLD_OUT);

        // when
        LaptopDetailResponse found = findLaptopById(laptop.getId());

        // then
        assertThat(found.offers()).extracting(OfferResponse::price)
            .containsExactly(2_850_000L, 2_990_000L);
    }

    @Test
    void 없는_id로_상세를_조회하면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> findLaptopById(-1L))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(BusinessErrorCode.LAPTOP_NOT_FOUND);
    }

    @Test
    void 판매_중인_오퍼가_없는_노트북의_상세를_조회하면_예외가_발생한다() {
        // given
        Laptop laptop = saveLaptopWithoutOffer("판매종료", Os.WINDOWS, 10000, 16, 512);
        saveOffer(laptop, 900_000L, OfferStatus.DISCONTINUED);

        // when & then
        assertThatThrownBy(() -> findLaptopById(laptop.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(BusinessErrorCode.LAPTOP_NOT_FOUND);
    }

    private LaptopDetailResponse findLaptopById(Long id) {
        flushAndClear();
        return laptopService.findLaptopById(id);
    }

    private Slice<LaptopSummaryResponse> findLaptops() {
        flushAndClear();
        return laptopService.findLaptops(
            new LaptopSearchCondition(null, null, null, null), 0, 10);
    }
}
