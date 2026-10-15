package com.wrb.devica.product.service;

import static com.wrb.devica.fixture.CpuFixture.cpu;
import static com.wrb.devica.fixture.LaptopFixture.laptop;
import static com.wrb.devica.fixture.LaptopSearchConditionFixture.condition;
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
import com.wrb.devica.product.dto.ProductSummaryResponse;
import com.wrb.devica.product.repository.CpuRepository;
import com.wrb.devica.product.repository.LaptopRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Slice;

@JpaSliceTest
@Import({ProductService.class, ProductImageUrlResolver.class, ProductOfferService.class})
class ProductServiceTest {

    private static final String CATEGORY = "LAPTOP";

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

    private Slice<ProductSummaryResponse> findLaptops() {
        entityManager.flush();
        entityManager.clear();
        return productService.findProductsByCategory(CATEGORY,
            condition().build(), new PageCondition(0, 10, null));
    }

    private Cpu saveCpu() {
        return cpuRepository.save(cpu().build());
    }

    private Laptop laptopOf(LaptopBuilder builder) {
        return laptopRepository.save(builder.category(category).cpu(cpu).build());
    }
}
