package com.wrb.devica.product;

import static com.wrb.devica.fixture.CpuFixture.cpu;
import static com.wrb.devica.fixture.LaptopFixture.laptop;
import static com.wrb.devica.fixture.LaptopSearchConditionFixture.condition;
import static com.wrb.devica.fixture.ProductOfferFixture.offer;
import static com.wrb.devica.fixture.ProductOfferFixture.onSaleOffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.wrb.devica.category.ProductCategory;
import com.wrb.devica.category.ProductCategoryCode;
import com.wrb.devica.category.ProductCategoryRepository;
import com.wrb.devica.common.JpaSliceTest;
import com.wrb.devica.fixture.LaptopFixture.LaptopBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

@JpaSliceTest
class LaptopRepositoryCustomTest {

    private static final long DEFAULT_PRICE = 1_000_000L;

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private CpuRepository cpuRepository;

    @Autowired
    private ProductOfferRepository productOfferRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private ProductCategory category;

    private Cpu cpu;
    @Autowired
    private LaptopRepository laptopRepository;

    @BeforeEach
    void setUpCategory() {
        category = productCategoryRepository.save(ProductCategory.from(ProductCategoryCode.LAPTOP));
        cpu = saveCpu();
    }

    @Test
    void 조건이_없을_때_조회하면_전체를_id_오름차순으로_반환한다() {
        // given
        onSaleLaptop(laptop().name("A"));
        onSaleLaptop(laptop().name("B"));

        // when
        Slice<ProductSummaryResponse> found = findLaptops(0, 10);

        // then
        assertThat(found.getContent()).extracting(ProductSummaryResponse::name).containsExactly("A", "B");
        assertThat(found.hasNext()).isFalse();
    }

    @Test
    void os를_지정할_때_조회하면_해당_os만_반환한다() {
        // given
        onSaleLaptop(laptop().name("윈도우").os(Os.WINDOWS));
        onSaleLaptop(laptop().name("맥").os(Os.MAC));

        // when
        Slice<ProductSummaryResponse> found = findLaptops(condition().os(Os.MAC).build());

        // then
        assertThat(found.getContent()).extracting(ProductSummaryResponse::name).containsExactly("맥");
    }

    @Test
    void cpu_등급을_지정할_때_조회하면_그_등급의_최소_점수_이상만_반환한다() {
        // given
        int minScore = CpuTier.P_HS.getMinScore();
        onSaleLaptop(laptop().name("낮음"), saveCpu(minScore - 1), DEFAULT_PRICE);
        onSaleLaptop(laptop().name("중간"), saveCpu(minScore), DEFAULT_PRICE);
        onSaleLaptop(laptop().name("높음"), saveCpu(minScore + 1), DEFAULT_PRICE);

        // when
        Slice<ProductSummaryResponse> found = findLaptops(condition().cpuTier(CpuTier.P_HS).build());

        // then
        assertThat(found.getContent()).extracting(ProductSummaryResponse::name).containsExactly("중간", "높음");
    }

    @ParameterizedTest
    @ValueSource(strings = {"gram", "GRAM", "프로", "LG", "lg"})
    void 검색어가_브랜드나_제품명에_포함되면_조회한다(String keyword) {
        // given
        onSaleLaptop(laptop().brand("LG").name("gram 프로 16"));
        onSaleLaptop(laptop().brand("Apple").name("MacBook Air").os(Os.MAC));

        // when
        Slice<ProductSummaryResponse> found = findLaptops(condition().keyword(keyword).build());

        // then
        assertThat(found.getContent()).extracting(ProductSummaryResponse::name).containsExactly("gram 프로 16");
    }

    @Test
    void 검색어가_공백뿐일_때_조회하면_조건으로_보지_않는다() {
        // given
        onSaleLaptop(laptop().name("A"));
        onSaleLaptop(laptop().name("B"));

        // when
        Slice<ProductSummaryResponse> found = findLaptops(condition().keyword("   ").build());

        // then
        assertThat(found.getContent()).extracting(ProductSummaryResponse::name).containsExactly("A", "B");
    }

    @Test
    void 브랜드를_지정할_때_조회하면_완전히_일치하는_것만_반환한다() {
        // given
        onSaleLaptop(laptop().brand("LG").name("그램"));
        onSaleLaptop(laptop().brand("LG전자").name("그램2"));

        // when
        Slice<ProductSummaryResponse> found = findLaptops(condition().brand("LG").build());

        // then
        assertThat(found.getContent()).extracting(ProductSummaryResponse::name).containsExactly("그램");
    }

    @Test
    void 최소_가격을_지정할_때_조회하면_최저가가_그_이상인_것만_반환한다() {
        // given
        onSaleLaptop(laptop().name("싼것"), 1_000_000L);
        onSaleLaptop(laptop().name("같은것"), 2_000_000L);
        onSaleLaptop(laptop().name("비싼것"), 3_000_000L);

        // when
        Slice<ProductSummaryResponse> found = findLaptops(
            condition().minPrice(2_000_000L).build());

        // then
        assertThat(found.getContent()).extracting(ProductSummaryResponse::name)
            .containsExactly("같은것", "비싼것");
    }

    @Test
    void 최대_가격을_지정할_때_조회하면_최저가가_그_이하인_것만_반환한다() {
        // given
        onSaleLaptop(laptop().name("싼것"), 1_000_000L);
        onSaleLaptop(laptop().name("같은것"), 2_000_000L);
        onSaleLaptop(laptop().name("비싼것"), 3_000_000L);

        // when
        Slice<ProductSummaryResponse> found = findLaptops(
            condition().maxPrice(2_000_000L).build());

        // then
        assertThat(found.getContent()).extracting(ProductSummaryResponse::name)
            .containsExactly("싼것", "같은것");
    }

    @Test
    void 가격_범위를_지정할_때_조회하면_그_사이만_반환한다() {
        // given
        onSaleLaptop(laptop().name("아래"), 1_000_000L);
        onSaleLaptop(laptop().name("안"), 2_000_000L);
        onSaleLaptop(laptop().name("위"), 3_000_000L);

        // when
        Slice<ProductSummaryResponse> found = findLaptops(
            condition().minPrice(1_500_000L).maxPrice(2_500_000L).build());

        // then
        assertThat(found.getContent()).extracting(ProductSummaryResponse::name).containsExactly("안");
    }

    @Test
    void 가격_조건은_판매_중인_오퍼의_최저가를_기준으로_한다() {
        // given
        Laptop laptop = laptopOf(laptop().name("노트북"));
        productOfferRepository.save(offer().product(laptop).price(3_000_000L).status(OfferStatus.ON_SALE).build());
        productOfferRepository.save(offer().product(laptop).price(2_000_000L).status(OfferStatus.ON_SALE).build());
        productOfferRepository.save(offer().product(laptop).price(1_000_000L).status(OfferStatus.SOLD_OUT).build());

        // when
        Slice<ProductSummaryResponse> found = findLaptops(
            condition().maxPrice(1_500_000L).build());

        // then
        assertThat(found.getContent()).isEmpty();
    }

    @Test
    void 조건을_여러_개_지정할_때_조회하면_모두_만족하는_것만_반환한다() {
        // given
        int enough = CpuTier.BASIC.getMinScore();
        onSaleLaptop(laptop().brand("LG").name("그램 16").os(Os.MAC), saveCpu(enough), DEFAULT_PRICE);
        onSaleLaptop(laptop().brand("LG").name("그램 저사양").os(Os.MAC), saveCpu(5000), DEFAULT_PRICE);
        onSaleLaptop(laptop().brand("LG").name("울트라 PC").os(Os.MAC), saveCpu(enough), DEFAULT_PRICE);
        onSaleLaptop(laptop().brand("Apple").name("그램과 비슷한 것").os(Os.MAC), saveCpu(enough), DEFAULT_PRICE);
        onSaleLaptop(laptop().brand("LG").name("그램 윈도우"), saveCpu(enough), DEFAULT_PRICE);

        // when
        Slice<ProductSummaryResponse> found = findLaptops(condition().os(Os.MAC).cpuTier(CpuTier.BASIC)
            .memoryGb(16).storageGb(512).keyword("그램").brand("LG").build());

        // then
        assertThat(found.getContent()).extracting(ProductSummaryResponse::name).containsExactly("그램 16");
    }

    @Test
    void 페이지를_넘겨_조회하면_이어지는_결과와_hasNext를_반환한다() {
        // given
        onSaleLaptop(laptop().name("1"));
        onSaleLaptop(laptop().name("2"));
        onSaleLaptop(laptop().name("3"));

        // when
        Slice<ProductSummaryResponse> firstPage = findLaptops(0, 2);
        Slice<ProductSummaryResponse> lastPage = findLaptops(1, 2);

        // then
        assertThat(firstPage.getContent()).extracting(ProductSummaryResponse::name).containsExactly("1", "2");
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(lastPage.getContent()).extracting(ProductSummaryResponse::name).containsExactly("3");
        assertThat(lastPage.hasNext()).isFalse();
    }

    @Test
    void 조건에_맞는_것이_없을_때_조회하면_빈_결과를_반환한다() {
        // given
        onSaleLaptop(laptop().name("유일"));

        // when
        Slice<ProductSummaryResponse> found = findLaptops(condition().os(Os.MAC).build());

        // then
        assertThat(found.getContent()).isEmpty();
        assertThat(found.hasNext()).isFalse();
    }

    @Test
    void 페이지가_범위를_넘을_때_조회하면_빈_결과를_반환한다() {
        // given
        onSaleLaptop(laptop().name("1"));
        onSaleLaptop(laptop().name("2"));

        // when
        Slice<ProductSummaryResponse> found = findLaptops(5, 10);

        // then
        assertThat(found.getContent()).isEmpty();
        assertThat(found.hasNext()).isFalse();
    }

    @Test
    void 판매_중인_오퍼가_없어도_조회하고_최저가는_비워둔다() {
        // given
        onSaleLaptop(laptop().name("판매중"));
        laptopOf(laptop().name("오퍼없음"));
        Laptop soldOut = laptopOf(laptop().name("품절"));
        productOfferRepository.save(offer().product(soldOut).price(1_000_000L).status(OfferStatus.SOLD_OUT).build());
        productOfferRepository.save(offer().product(soldOut).price(1_000_000L).status(OfferStatus.DISCONTINUED).build());

        // when
        Slice<ProductSummaryResponse> found = findLaptops(0, 10);

        // then
        assertThat(found.getContent())
            .extracting(ProductSummaryResponse::name, ProductSummaryResponse::minPrice)
            .containsExactly(
                tuple("판매중", DEFAULT_PRICE),
                tuple("오퍼없음", null),
                tuple("품절", null)
            );
    }

    @Test
    void 가격_조건을_지정할_때_조회하면_최저가가_없는_노트북은_제외한다() {
        // given
        onSaleLaptop(laptop().name("판매중"), 1_000_000L);
        laptopOf(laptop().name("오퍼없음"));

        // when
        Slice<ProductSummaryResponse> found = findLaptops(
            condition().minPrice(0L).build());

        // then
        assertThat(found.getContent()).extracting(ProductSummaryResponse::name).containsExactly("판매중");
    }

    @Test
    void 가격_낮은_순으로_조회하면_최저가_오름차순으로_반환한다() {
        // given
        onSaleLaptop(laptop().name("비쌈"), 3_000_000L);
        onSaleLaptop(laptop().name("쌈"), 1_000_000L);
        onSaleLaptop(laptop().name("중간"), 2_000_000L);

        // when
        Slice<ProductSummaryResponse> found = findLaptops(SortType.PRICE_ASC);

        // then
        assertThat(found.getContent()).extracting(ProductSummaryResponse::name).containsExactly("쌈", "중간", "비쌈");
    }

    @Test
    void 가격_높은_순으로_조회하면_최저가_내림차순으로_반환한다() {
        // given
        onSaleLaptop(laptop().name("쌈"), 1_000_000L);
        onSaleLaptop(laptop().name("비쌈"), 3_000_000L);
        onSaleLaptop(laptop().name("중간"), 2_000_000L);

        // when
        Slice<ProductSummaryResponse> found = findLaptops(SortType.PRICE_DESC);

        // then
        assertThat(found.getContent()).extracting(ProductSummaryResponse::name).containsExactly("비쌈", "중간", "쌈");
    }

    @Test
    void 가격순은_판매_중인_오퍼의_최저가를_기준으로_한다() {
        // given
        Laptop soldOutCheaper = laptopOf(laptop().name("품절이더쌈"));
        productOfferRepository.save(offer().product(soldOutCheaper).price(3_000_000L).status(OfferStatus.ON_SALE).build());
        productOfferRepository.save(offer().product(soldOutCheaper).price(500_000L).status(OfferStatus.SOLD_OUT).build());
        onSaleLaptop(laptop().name("판매중"), 2_000_000L);

        // when
        Slice<ProductSummaryResponse> found = findLaptops(SortType.PRICE_ASC);

        // then
        assertThat(found.getContent()).extracting(ProductSummaryResponse::name).containsExactly("판매중", "품절이더쌈");
    }

    @Test
    void 추천순으로_조회하면_CPU_메모리_저장_공간_순으로_사양이_높은_노트북을_먼저_반환한다() {
        // given
        onSaleLaptop(laptop().name("저장_공간만_큼").memoryGb(16).storageGb(4096), saveCpu(21_000), DEFAULT_PRICE);
        onSaleLaptop(laptop().name("메모리_큼").memoryGb(32).storageGb(512), saveCpu(21_000), DEFAULT_PRICE);
        onSaleLaptop(laptop().name("CPU_큼").memoryGb(16).storageGb(512), saveCpu(24_000), 3_000_000L);
        onSaleLaptop(laptop().name("메모리_같고_저장_공간_큼").memoryGb(32).storageGb(1024), saveCpu(21_000), DEFAULT_PRICE);

        // when
        Slice<ProductSummaryResponse> found = findLaptops(SortType.RECOMMENDED);

        // then
        assertThat(found.getContent()).extracting(ProductSummaryResponse::name)
            .containsExactly("CPU_큼", "메모리_같고_저장_공간_큼", "메모리_큼", "저장_공간만_큼");
    }

    @ParameterizedTest
    @EnumSource(SortType.class)
    void 정렬_기준을_지정할_때_정렬_값이_같으면_id_오름차순으로_반환한다(SortType sort) {
        // given
        onSaleLaptop(laptop().name("먼저"), 1_000_000L);
        onSaleLaptop(laptop().name("나중"), 1_000_000L);

        // when
        Slice<ProductSummaryResponse> found = findLaptops(sort);

        // then
        assertThat(found.getContent()).extracting(ProductSummaryResponse::name).containsExactly("먼저", "나중");
    }

    @ParameterizedTest
    @EnumSource(value = SortType.class, names = {"PRICE_ASC", "PRICE_DESC"})
    void 가격순으로_지정하면_최저가가_없는_노트북은_맨_뒤에_둔다(SortType sort) {
        // given
        laptopOf(laptop().name("오퍼없음"));
        onSaleLaptop(laptop().name("판매중"), 1_000_000L);

        // when
        Slice<ProductSummaryResponse> found = findLaptops(sort);

        // then
        assertThat(found.getContent()).extracting(ProductSummaryResponse::name).containsExactly("판매중", "오퍼없음");
    }

    private Laptop onSaleLaptop(LaptopBuilder builder) {
        return onSaleLaptop(builder, DEFAULT_PRICE);
    }

    private Laptop onSaleLaptop(LaptopBuilder builder, long price) {
        return onSaleLaptop(builder, cpu, price);
    }

    private Laptop onSaleLaptop(LaptopBuilder builder, Cpu cpu, long price) {
        Laptop laptop = laptopRepository.save(builder.category(category).cpu(cpu).build());
        productOfferRepository.save(onSaleOffer(laptop, price));
        return laptop;
    }

    private Laptop laptopOf(LaptopBuilder builder) {
        return laptopRepository.save(builder.category(category).cpu(cpu).build());
    }

    private Cpu saveCpu() {
        return cpuRepository.save(cpu().build());
    }

    private Cpu saveCpu(int score) {
        return cpuRepository.save(cpu().score(score).build());
    }

    private Slice<ProductSummaryResponse> findLaptops(LaptopSearchCondition condition) {
        return findLaptops(condition, 0, 10);
    }

    private Slice<ProductSummaryResponse> findLaptops(int page, int size) {
        return findLaptops(condition().build(), page, size);
    }

    private Slice<ProductSummaryResponse> findLaptops(SortType sort) {
        return findLaptops(condition().build(), sort, 0, 10);
    }

    private Slice<ProductSummaryResponse> findLaptops(LaptopSearchCondition condition, int page, int size) {
        return findLaptops(condition, null, page, size);
    }

    private Slice<ProductSummaryResponse> findLaptops(LaptopSearchCondition condition, SortType sort,
                                                     int page, int size) {
        entityManager.flush();
        entityManager.clear();
        return laptopRepository.findSummariesWithMinPriceForBE(condition, sort, PageRequest.of(page, size));
    }
}
