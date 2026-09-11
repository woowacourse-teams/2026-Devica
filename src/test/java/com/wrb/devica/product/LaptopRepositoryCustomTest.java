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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

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
        Slice<LaptopSummaryResponse> found = findLaptops(0, 10);

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("A", "B");
        assertThat(found.hasNext()).isFalse();
    }

    @Test
    void os를_지정할_때_조회하면_해당_os만_반환한다() {
        // given
        onSaleLaptop(laptop().name("윈도우").os(Os.WINDOWS));
        onSaleLaptop(laptop().name("맥").os(Os.MAC));

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(condition().os(Os.MAC).build());

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("맥");
    }

    @Test
    void cpu_점수를_지정할_때_조회하면_그_이상만_반환한다() {
        // given
        onSaleLaptop(laptop().name("낮음"), saveCpu(9999), DEFAULT_PRICE);
        onSaleLaptop(laptop().name("중간"), saveCpu(10000), DEFAULT_PRICE);
        onSaleLaptop(laptop().name("높음"), saveCpu(10001), DEFAULT_PRICE);

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(condition().cpuScore(10000).build());

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("중간", "높음");
    }

    @ParameterizedTest
    @ValueSource(strings = {"gram", "GRAM", "프로", "LG", "lg"})
    void 검색어가_브랜드나_제품명에_포함되면_조회한다(String keyword) {
        // given
        onSaleLaptop(laptop().brand("LG").name("gram 프로 16"));
        onSaleLaptop(laptop().brand("Apple").name("MacBook Air").os(Os.MAC));

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(condition().keyword(keyword).build());

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("gram 프로 16");
    }

    @Test
    void 검색어가_공백뿐일_때_조회하면_조건으로_보지_않는다() {
        // given
        onSaleLaptop(laptop().name("A"));
        onSaleLaptop(laptop().name("B"));

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(condition().keyword("   ").build());

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("A", "B");
    }

    @Test
    void 브랜드를_지정할_때_조회하면_완전히_일치하는_것만_반환한다() {
        // given
        onSaleLaptop(laptop().brand("LG").name("그램"));
        onSaleLaptop(laptop().brand("LG전자").name("그램2"));

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(condition().brand("LG").build());

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("그램");
    }

    @Test
    void 최소_가격을_지정할_때_조회하면_최저가가_그_이상인_것만_반환한다() {
        // given
        onSaleLaptop(laptop().name("싼것"), 1_000_000L);
        onSaleLaptop(laptop().name("같은것"), 2_000_000L);
        onSaleLaptop(laptop().name("비싼것"), 3_000_000L);

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(
            condition().minPrice(2_000_000L).build());

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name)
            .containsExactly("같은것", "비싼것");
    }

    @Test
    void 최대_가격을_지정할_때_조회하면_최저가가_그_이하인_것만_반환한다() {
        // given
        onSaleLaptop(laptop().name("싼것"), 1_000_000L);
        onSaleLaptop(laptop().name("같은것"), 2_000_000L);
        onSaleLaptop(laptop().name("비싼것"), 3_000_000L);

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(
            condition().maxPrice(2_000_000L).build());

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name)
            .containsExactly("싼것", "같은것");
    }

    @Test
    void 가격_범위를_지정할_때_조회하면_그_사이만_반환한다() {
        // given
        onSaleLaptop(laptop().name("아래"), 1_000_000L);
        onSaleLaptop(laptop().name("안"), 2_000_000L);
        onSaleLaptop(laptop().name("위"), 3_000_000L);

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(
            condition().minPrice(1_500_000L).maxPrice(2_500_000L).build());

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("안");
    }

    @Test
    void 가격_조건은_판매_중인_오퍼의_최저가를_기준으로_한다() {
        // given
        Laptop laptop = laptopOf(laptop().name("노트북"));
        productOfferRepository.save(offer().product(laptop).price(3_000_000L).status(OfferStatus.ON_SALE).build());
        productOfferRepository.save(offer().product(laptop).price(2_000_000L).status(OfferStatus.ON_SALE).build());
        productOfferRepository.save(offer().product(laptop).price(1_000_000L).status(OfferStatus.SOLD_OUT).build());

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(
            condition().maxPrice(1_500_000L).build());

        // then
        assertThat(found.getContent()).isEmpty();
    }

    @Test
    void 조건을_여러_개_지정할_때_조회하면_모두_만족하는_것만_반환한다() {
        // given
        onSaleLaptop(laptop().brand("LG").name("그램 16").os(Os.MAC), saveCpu(20000), DEFAULT_PRICE);
        onSaleLaptop(laptop().brand("LG").name("그램 저사양").os(Os.MAC), saveCpu(5000), DEFAULT_PRICE);
        onSaleLaptop(laptop().brand("LG").name("울트라 PC").os(Os.MAC), saveCpu(20000), DEFAULT_PRICE);
        onSaleLaptop(laptop().brand("Apple").name("그램과 비슷한 것").os(Os.MAC), saveCpu(20000), DEFAULT_PRICE);
        onSaleLaptop(laptop().brand("LG").name("그램 윈도우"), saveCpu(20000), DEFAULT_PRICE);

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(
            condition().os(Os.MAC).cpuScore(10000).memoryGb(16).storageGb(512).keyword("그램").brand("LG").build());

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("그램 16");
    }

    @Test
    void 페이지를_넘겨_조회하면_이어지는_결과와_hasNext를_반환한다() {
        // given
        onSaleLaptop(laptop().name("1"));
        onSaleLaptop(laptop().name("2"));
        onSaleLaptop(laptop().name("3"));

        // when
        Slice<LaptopSummaryResponse> firstPage = findLaptops(0, 2);
        Slice<LaptopSummaryResponse> lastPage = findLaptops(1, 2);

        // then
        assertThat(firstPage.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("1", "2");
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(lastPage.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("3");
        assertThat(lastPage.hasNext()).isFalse();
    }

    @Test
    void 조건에_맞는_것이_없을_때_조회하면_빈_결과를_반환한다() {
        // given
        onSaleLaptop(laptop().name("유일"));

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(condition().os(Os.MAC).build());

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
        Slice<LaptopSummaryResponse> found = findLaptops(5, 10);

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
        Slice<LaptopSummaryResponse> found = findLaptops(0, 10);

        // then
        assertThat(found.getContent())
            .extracting(LaptopSummaryResponse::name, LaptopSummaryResponse::minPrice)
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
        Slice<LaptopSummaryResponse> found = findLaptops(
            condition().minPrice(0L).build());

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("판매중");
    }

    private Slice<LaptopSummaryResponse> findLaptops(LaptopSearchCondition condition) {
        return findLaptops(condition, 0, 10);
    }

    private Slice<LaptopSummaryResponse> findLaptops(int page, int size) {
        return findLaptops(condition().build(), page, size);
    }

    private Slice<LaptopSummaryResponse> findLaptops(LaptopSearchCondition condition, int page, int size) {
        entityManager.flush();
        entityManager.clear();
        return laptopRepository.findSummariesWithMinPriceByCondition(condition, PageRequest.of(page, size, Sort.by("id")));
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

    private Cpu saveCpu(int score) {
        return cpuRepository.save(cpu().score(score).build());
    }
}
