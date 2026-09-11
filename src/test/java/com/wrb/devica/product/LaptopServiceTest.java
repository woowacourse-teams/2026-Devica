package com.wrb.devica.product;

import static com.wrb.devica.fixture.CpuFixture.cpu;
import static com.wrb.devica.fixture.LaptopFixture.laptop;
import static com.wrb.devica.fixture.LaptopSearchConditionFixture.condition;
import static com.wrb.devica.fixture.ProductOfferFixture.offer;
import static com.wrb.devica.fixture.ProductOfferFixture.onSaleOffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.wrb.devica.category.ProductCategory;
import com.wrb.devica.category.ProductCategoryCode;
import com.wrb.devica.category.ProductCategoryRepository;
import com.wrb.devica.common.BusinessErrorCode;
import com.wrb.devica.common.BusinessException;
import com.wrb.devica.common.JpaSliceTest;
import com.wrb.devica.fixture.LaptopFixture.LaptopBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Slice;

@JpaSliceTest
@Import(LaptopService.class)
class LaptopServiceTest {

    private static final long DEFAULT_PRICE = 1_000_000L;

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private CpuRepository cpuRepository;

    @Autowired
    private ProductOfferRepository productOfferRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private LaptopRepository laptopRepository;

    private ProductCategory category;

    private Cpu cpu;
    @Autowired
    private LaptopService laptopService;

    @BeforeEach
    void setUpCategory() {
        category = productCategoryRepository.save(ProductCategory.from(ProductCategoryCode.LAPTOP));
        cpu = saveCpu();
    }

    @Test
    void 오퍼가_여러_개일_때_조회하면_가장_낮은_가격을_반환한다() {
        // given
        Laptop laptop = laptopOf(laptop().name("노트북"));

        productOfferRepository.save(offer().product(laptop).price(2_500_000L).status(OfferStatus.ON_SALE).build());
        productOfferRepository.save(offer().product(laptop).price(1_900_000L).status(OfferStatus.ON_SALE).build());
        productOfferRepository.save(offer().product(laptop).price(2_100_000L).status(OfferStatus.ON_SALE).build());

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
        Laptop laptop = laptopOf(laptop().name("노트북"));

        productOfferRepository.save(offer().product(laptop).price(900_000L).status(OfferStatus.SOLD_OUT).build());
        productOfferRepository.save(offer().product(laptop).price(800_000L).status(OfferStatus.DISCONTINUED).build());
        productOfferRepository.save(offer().product(laptop).price(2_000_000L).status(OfferStatus.ON_SALE).build());

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
        Laptop first = laptopOf(laptop().name("첫번째"));
        productOfferRepository.save(offer().product(first).price(1_000_000L).status(OfferStatus.ON_SALE).build());
        productOfferRepository.save(offer().product(first).price(1_200_000L).status(OfferStatus.ON_SALE).build());

        Laptop second = laptopOf(laptop().name("두번째").memoryGb(32).storageGb(1024));
        productOfferRepository.save(offer().product(second).price(3_000_000L).status(OfferStatus.ON_SALE).build());
        productOfferRepository.save(offer().product(second).price(2_800_000L).status(OfferStatus.ON_SALE).build());

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
        Laptop laptop = laptopOf(laptop().name("gram Pro 16").memoryGb(32).storageGb(1024));
        productOfferRepository.save(offer().product(laptop).price(2_850_000L).status(OfferStatus.ON_SALE).build());

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
        Laptop laptop = laptopOf(laptop().name("gram Pro 16").memoryGb(32).storageGb(1024));

        productOfferRepository.save(offer().product(laptop).price(2_990_000L).status(OfferStatus.ON_SALE).build());
        productOfferRepository.save(offer().product(laptop).price(2_850_000L).status(OfferStatus.ON_SALE).build());
        productOfferRepository.save(offer().product(laptop).price(2_500_000L).status(OfferStatus.SOLD_OUT).build());

        entityManager.flush();
        entityManager.clear();

        // when
        LaptopDetailResponse found = laptopService.findLaptopById(laptop.getId());

        // then
        assertThat(found.offers()).extracting(OfferResponse::price)
            .containsExactly(2_850_000L, 2_990_000L);
    }

    @Test
    void 없는_id로_상세를_조회하면_예외가_발생한다() {
        //given
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> laptopService.findLaptopById(-1L))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(BusinessErrorCode.LAPTOP_NOT_FOUND);
    }

    @Test
    void 판매_중인_오퍼가_없는_노트북의_상세를_조회하면_예외가_발생한다() {
        // given
        Laptop laptop = laptopOf(laptop().name("판매종료"));
        productOfferRepository.save(offer().product(laptop).price(900_000L).status(OfferStatus.DISCONTINUED).build());


        // when & then
        assertThatThrownBy(() -> findLaptopById(laptop.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(BusinessErrorCode.LAPTOP_NOT_FOUND);
    }

    private LaptopDetailResponse findLaptopById(Long id) {
        entityManager.flush();
        entityManager.clear();
        return laptopService.findLaptopById(id);
    }

    private Slice<LaptopSummaryResponse> findLaptops() {
        entityManager.flush();
        entityManager.clear();
        return laptopService.findLaptops(
            condition().build(), new LaptopPageCondition(0, 10));
    }

    private Cpu saveCpu() {
        return cpuRepository.save(cpu().build());
    }

    private Laptop onSaleLaptop(LaptopBuilder builder) {
        return onSaleLaptop(builder, DEFAULT_PRICE);
    }

    private Laptop onSaleLaptop(LaptopBuilder builder, long price) {
        Laptop laptop = laptopRepository.save(builder.category(category).cpu(cpu).build());
        productOfferRepository.save(onSaleOffer(laptop, price));
        return laptop;
    }

    private Laptop laptopOf(LaptopBuilder builder) {
        return laptopRepository.save(builder.category(category).cpu(cpu).build());
    }

    private Laptop onSaleLaptop(LaptopBuilder builder, Cpu cpu, long price) {
        Laptop laptop = laptopRepository.save(builder.category(category).cpu(cpu).build());
        productOfferRepository.save(onSaleOffer(laptop, price));
        return laptop;
    }
}
