package com.wrb.devica.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.category.ProductCategory;
import com.wrb.devica.category.ProductCategoryCode;
import com.wrb.devica.common.JpaSliceTest;
import com.wrb.devica.fixture.CpuFixture;
import com.wrb.devica.fixture.FixtureSaver;
import com.wrb.devica.fixture.LaptopFixture;
import com.wrb.devica.fixture.ProductOfferFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

@JpaSliceTest
@Import(FixtureSaver.class)
class LaptopRepositoryCustomTest {

    @Autowired
    private FixtureSaver saver;

    private ProductCategory category;

    private Cpu cpu;
    @Autowired
    private LaptopRepository laptopRepository;

    @BeforeEach
    void setUpCategory() {
        category = saver.save(ProductCategory.from(ProductCategoryCode.LAPTOP));
        cpu = saveCpu();
    }

    @Test
    void 조건이_없을_때_조회하면_전체를_id_오름차순으로_반환한다() {
        // given
        saver.save(
            ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("A").build())));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("B").build())));

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(0, 10);

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("A", "B");
        assertThat(found.hasNext()).isFalse();
    }

    @Test
    void os를_지정할_때_조회하면_해당_os만_반환한다() {
        // given
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("윈도우").os(Os.WINDOWS).build())));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("맥").os(Os.MAC).build())));

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(new LaptopSearchCondition(Os.MAC, null,
            null, null, null, null, null, null));

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("맥");
    }

    @Test
    void cpu_점수를_지정할_때_조회하면_그_이상만_반환한다() {
        // given
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("낮음").cpu(saver.save(CpuFixture.cpu().score(9999).build())).build())));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("중간").cpu(saver.save(CpuFixture.cpu().score(10000).build())).build())));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("높음").cpu(saver.save(CpuFixture.cpu().score(10001).build())).build())));

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(new LaptopSearchCondition(null,
            10000, null, null, null, null, null, null));

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("중간", "높음");
    }

    @ParameterizedTest
    @ValueSource(strings = {"gram", "GRAM", "프로", "LG", "lg"})
    void 검색어가_브랜드나_제품명에_포함되면_조회한다(String keyword) {
        // given
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).brand("LG").name("gram 프로 16").build())));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).brand("Apple").name("MacBook Air").os(Os.MAC).build())));

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(new LaptopSearchCondition(null,
            null, null, null, keyword, null, null, null));

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("gram 프로 16");
    }

    @Test
    void 검색어가_공백뿐일_때_조회하면_조건으로_보지_않는다() {
        // given
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("A").build())));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("B").build())));

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(new LaptopSearchCondition(null,
            null, null, null, "   ", null, null, null));

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("A", "B");
    }

    @Test
    void 브랜드를_지정할_때_조회하면_완전히_일치하는_것만_반환한다() {
        // given
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).brand("LG").name("그램").build())));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).brand("LG전자").name("그램2").build())));

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(new LaptopSearchCondition(null,
            null, null, null, null, "LG", null, null));

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("그램");
    }

    @Test
    void 최소_가격을_지정할_때_조회하면_최저가가_그_이상인_것만_반환한다() {
        // given
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("싼것").build()), 1_000_000L));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("같은것").build()), 2_000_000L));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("비싼것").build()), 3_000_000L));

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(
            new LaptopSearchCondition(null, null, null, null, null, null, 2_000_000L, null));

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name)
            .containsExactly("같은것", "비싼것");
    }

    @Test
    void 최대_가격을_지정할_때_조회하면_최저가가_그_이하인_것만_반환한다() {
        // given
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("싼것").build()), 1_000_000L));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("같은것").build()), 2_000_000L));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("비싼것").build()), 3_000_000L));

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(
            new LaptopSearchCondition(null, null, null, null, null, null, null, 2_000_000L));

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name)
            .containsExactly("싼것", "같은것");
    }

    @Test
    void 가격_범위를_지정할_때_조회하면_그_사이만_반환한다() {
        // given
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("아래").build()), 1_000_000L));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("안").build()), 2_000_000L));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("위").build()), 3_000_000L));

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(
            new LaptopSearchCondition(null, null, null, null, null, null, 1_500_000L, 2_500_000L));

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("안");
    }

    @Test
    void 가격_조건은_판매_중인_오퍼의_최저가를_기준으로_한다() {
        // given
        Laptop laptop = saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("노트북").build());
        saver.save(ProductOfferFixture.offer().product(laptop).price(3_000_000L).status(OfferStatus.ON_SALE).build());
        saver.save(ProductOfferFixture.offer().product(laptop).price(2_000_000L).status(OfferStatus.ON_SALE).build());
        saver.save(ProductOfferFixture.offer().product(laptop).price(1_000_000L).status(OfferStatus.SOLD_OUT).build());

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(
            new LaptopSearchCondition(null, null, null, null, null, null, null, 1_500_000L));

        // then
        assertThat(found.getContent()).isEmpty();
    }

    @Test
    void 조건을_여러_개_지정할_때_조회하면_모두_만족하는_것만_반환한다() {
        // given
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).brand("LG").name("그램 16").os(Os.MAC)
            .cpu(saver.save(CpuFixture.cpu().score(20000).build())).memoryGb(32).storageGb(1024).build())));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).brand("LG").name("그램 저사양").os(Os.MAC)
            .cpu(saver.save(CpuFixture.cpu().score(5000).build())).memoryGb(32).storageGb(1024).build())));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).brand("LG").name("울트라 PC").os(Os.MAC)
            .cpu(saver.save(CpuFixture.cpu().score(20000).build())).memoryGb(32).storageGb(1024).build())));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).brand("Apple").name("그램과 비슷한 것").os(Os.MAC)
            .cpu(saver.save(CpuFixture.cpu().score(20000).build())).memoryGb(32).storageGb(1024).build())));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).brand("LG").name("그램 윈도우")
            .cpu(saver.save(CpuFixture.cpu().score(20000).build())).memoryGb(32).storageGb(1024).build())));

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(
            new LaptopSearchCondition(Os.MAC, 10000, 16, 512, "그램", "LG", null, null));

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("그램 16");
    }

    @Test
    void 페이지를_넘겨_조회하면_이어지는_결과와_hasNext를_반환한다() {
        // given
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("1").build())));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("2").build())));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("3").build())));

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
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("유일").build())));

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(new LaptopSearchCondition(Os.MAC, null, null, null, null, null, null, null));

        // then
        assertThat(found.getContent()).isEmpty();
        assertThat(found.hasNext()).isFalse();
    }

    @Test
    void 페이지가_범위를_넘을_때_조회하면_빈_결과를_반환한다() {
        // given
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("1").build())));
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("2").build())));

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(5, 10);

        // then
        assertThat(found.getContent()).isEmpty();
        assertThat(found.hasNext()).isFalse();
    }

    @Test
    void 판매_중인_오퍼가_없을_때_조회하면_결과에서_제외된다() {
        // given
        saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("판매중").build())));
        saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("오퍼없음").build());
        Laptop soldOut = saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("품절").build());
        saver.save(ProductOfferFixture.offer().product(soldOut).price(1_000_000L).status(OfferStatus.SOLD_OUT).build());
        saver.save(ProductOfferFixture.offer().product(soldOut).price(1_000_000L).status(OfferStatus.DISCONTINUED).build());

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(0, 10);

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("판매중");
    }

    private Slice<LaptopSummaryResponse> findLaptops(LaptopSearchCondition condition) {
        return findLaptops(condition, 0, 10);
    }

    private Slice<LaptopSummaryResponse> findLaptops(int page, int size) {
        return findLaptops(new LaptopSearchCondition(null, null,
            null, null, null, null, null, null), page, size);
    }

    private Slice<LaptopSummaryResponse> findLaptops(LaptopSearchCondition condition, int page, int size) {
        saver.flushAndClear();
        return laptopRepository.findOnSaleSummariesWithMinPriceByCondition(condition, PageRequest.of(page, size, Sort.by("id")));
    }

    private Cpu saveCpu() {
        return saver.save(CpuFixture.cpu().build());
    }
}
