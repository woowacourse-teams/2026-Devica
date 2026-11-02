package com.wrb.devica.product.service;

import static com.wrb.devica.fixture.CpuFixture.cpu;
import static com.wrb.devica.fixture.LaptopFixture.laptop;
import static com.wrb.devica.fixture.LaptopSearchConditionFixture.condition;
import static com.wrb.devica.fixture.ProductOfferFixture.onSaleOffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrb.devica.category.domain.ProductCategory;
import com.wrb.devica.category.domain.ProductCategoryCode;
import com.wrb.devica.category.repository.ProductCategoryRepository;
import com.wrb.devica.common.JpaSliceTest;
import com.wrb.devica.common.exception.BusinessErrorCode;
import com.wrb.devica.common.exception.BusinessException;
import com.wrb.devica.fixture.LaptopFixture.LaptopBuilder;
import com.wrb.devica.product.domain.Cpu;
import com.wrb.devica.product.domain.Laptop;
import com.wrb.devica.product.dto.PageCondition;
import com.wrb.devica.product.repository.CpuRepository;
import com.wrb.devica.product.repository.LaptopRepository;
import com.wrb.devica.product.repository.ProductMinPriceRepository;
import com.wrb.devica.product.repository.ProductOfferRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@JpaSliceTest
@Import({ProductService.class, ProductImageUrlResolver.class, ProductOfferService.class})
class ProductServiceTest {

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private CpuRepository cpuRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private LaptopRepository laptopRepository;

    private ProductCategory category;

    private Cpu cpu;
    @Autowired
    private ProductService productService;

    @Autowired
    private ProductOfferRepository productOfferRepository;

    @Autowired
    private ProductMinPriceRepository productMinPriceRepository;

    @BeforeEach
    void setUpCategory() {
        category = productCategoryRepository.save(ProductCategory.from(ProductCategoryCode.LAPTOP));
        cpu = saveCpu();
    }

    @Test
    void 없는_카테고리로_목록을_조회하면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> productService.findProductsByCategory("UNKNOWN",
            condition().build(), new PageCondition(0, 10, null)))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(BusinessErrorCode.PRODUCT_CATEGORY_NOT_FOUND);
    }

    @Test
    void 없는_카테고리로_브랜드를_조회하면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> productService.findBrandsByCategory("UNKNOWN", null))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(BusinessErrorCode.PRODUCT_CATEGORY_NOT_FOUND);
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

    @Test
    void 페이지의_판매처는_한번에_조회하고_빈_페이지는_추가_조회하지_않는다() {
        for (int i = 0; i < 3; i++) {
            Laptop laptop = laptopOf(laptop());
            productOfferRepository.saveAndFlush(onSaleOffer(laptop, 1_000_000L + i));
            productMinPriceRepository.refresh(laptop.getId());
        }
        entityManager.flush();
        entityManager.clear();
        var stats = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean enabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            stats.clear();
            var page = productService.findProductsByCategory("LAPTOP", condition().build(), new PageCondition(0, 10, null));
            assertThat(page.getContent()).hasSize(3).allSatisfy(row -> {
                assertThat(row.minPriceOffer()).isNotNull();
                assertThat(row.minPriceOffer().price()).isEqualTo(row.minPrice());
            });
            assertThat(stats.getPrepareStatementCount()).isEqualTo(2);

            stats.clear();
            assertThat(productService.findProductsByCategory("LAPTOP", condition().build(), new PageCondition(5, 10, null)))
                .isEmpty();
            assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
        } finally {
            stats.setStatisticsEnabled(enabled);
        }
    }

    @Test
    void 가격이_없는_제품에는_최저가_판매처도_없다() {
        laptopOf(laptop());
        var rows = productService.findProductsByCategory("LAPTOP", condition().build(), new PageCondition(0, 10, null));
        assertThat(rows.getContent()).singleElement().satisfies(row -> {
            assertThat(row.minPrice()).isNull();
            assertThat(row.minPriceOffer()).isNull();
        });
    }

    private Cpu saveCpu() {
        return cpuRepository.save(cpu().build());
    }

    private Laptop laptopOf(LaptopBuilder builder) {
        return laptopRepository.save(builder.category(category).cpu(cpu).build());
    }
}
