package com.wrb.devica.product;

import static com.wrb.devica.fixture.CpuFixture.cpu;
import static com.wrb.devica.fixture.LaptopFixture.laptop;
import static com.wrb.devica.fixture.LaptopSearchConditionFixture.condition;
import static com.wrb.devica.fixture.ProductOfferFixture.offer;
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
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Slice;

@JpaSliceTest
@Import(ProductService.class)
class ProductServiceTest {

    private static final String PURPOSE = "BACKEND_DEVELOPMENT";

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
    private ProductService productService;

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
        Slice<ProductSummaryResponse> found = findLaptops();

        // then
        assertThat(found.getContent()).singleElement()
            .extracting(ProductSummaryResponse::minPrice)
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
        Slice<ProductSummaryResponse> found = findLaptops();

        // then
        assertThat(found.getContent()).singleElement()
            .extracting(ProductSummaryResponse::minPrice)
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
        Slice<ProductSummaryResponse> found = findLaptops();

        // then
        assertThat(found.getContent())
            .extracting(ProductSummaryResponse::name, ProductSummaryResponse::minPrice)
            .containsExactly(
                tuple("첫번째", 1_000_000L),
                tuple("두번째", 2_800_000L)
            );
    }

    @Test
    void 없는_id로_상세를_조회하면_예외가_발생한다() {
        //given
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> productService.findProductById(-1L))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(BusinessErrorCode.LAPTOP_NOT_FOUND);
    }

    @Test
    void 상세를_조회하면_CPU_까지_채워서_반환한다() {
        // given
        Laptop laptop = laptopOf(laptop().name("노트북"));
        entityManager.flush();
        entityManager.clear();

        // when
        Laptop found = (Laptop) productService.findProductById(laptop.getId());

        // then
        assertThat(Hibernate.isInitialized(found.getCpu())).isTrue();
    }

    private Slice<ProductSummaryResponse> findLaptops() {
        entityManager.flush();
        entityManager.clear();
        return productService.findProductsByPurpose(PURPOSE,
            condition().build(), new PageCondition(0, 10, null));
    }

    private Cpu saveCpu() {
        return cpuRepository.save(cpu().build());
    }

    private Laptop laptopOf(LaptopBuilder builder) {
        return laptopRepository.save(builder.category(category).cpu(cpu).build());
    }
}
