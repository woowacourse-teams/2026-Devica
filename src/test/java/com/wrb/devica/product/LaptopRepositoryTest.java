package com.wrb.devica.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

class LaptopRepositoryTest extends LaptopJpaTestSupport {

    @Autowired
    private LaptopRepository laptopRepository;

    @Test
    void 조건이_없을_때_조회하면_전체를_id_오름차순으로_반환한다() {
        // given
        offer().product(laptop().name("A").save()).save();
        offer().product(laptop().name("B").save()).save();

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(0, 10);

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("A", "B");
        assertThat(found.hasNext()).isFalse();
    }

    @Test
    void os를_지정할_때_조회하면_해당_os만_반환한다() {
        // given
        offer().product(laptop().name("윈도우").os(Os.WINDOWS).save()).save();
        offer().product(laptop().name("맥").os(Os.MAC).save()).save();

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(new LaptopSearchCondition(Os.MAC, null,
            null, null, null, null, null, null));

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("맥");
    }

    @Test
    void cpu_점수를_지정할_때_조회하면_그_이상만_반환한다() {
        // given
        offer().product(laptop().name("낮음").cpuScore(9999).save()).save();
        offer().product(laptop().name("중간").cpuScore(10000).save()).save();
        offer().product(laptop().name("높음").cpuScore(10001).save()).save();

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
        offer().product(laptop().brand("LG").name("gram 프로 16").save()).save();
        offer().product(laptop().brand("Apple").name("MacBook Air").os(Os.MAC).save()).save();

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(new LaptopSearchCondition(null,
            null, null, null, keyword, null, null, null));

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("gram 프로 16");
    }

    @Test
    void 검색어가_공백뿐일_때_조회하면_조건으로_보지_않는다() {
        // given
        offer().product(laptop().name("A").save()).save();
        offer().product(laptop().name("B").save()).save();

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(new LaptopSearchCondition(null,
            null, null, null, "   ", null, null, null));

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("A", "B");
    }

    @Test
    void 브랜드를_지정할_때_조회하면_완전히_일치하는_것만_반환한다() {
        // given
        offer().product(laptop().brand("LG").name("그램").save()).save();
        offer().product(laptop().brand("LG전자").name("그램2").save()).save();

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(new LaptopSearchCondition(null,
            null, null, null, null, "LG", null, null));

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("그램");
    }

    @Test
    void 최소_가격을_지정할_때_조회하면_최저가가_그_이상인_것만_반환한다() {
        // given
        offer().product(laptop().name("싼것").save()).price(1_000_000L).save();
        offer().product(laptop().name("같은것").save()).price(2_000_000L).save();
        offer().product(laptop().name("비싼것").save()).price(3_000_000L).save();

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
        offer().product(laptop().name("싼것").save()).price(1_000_000L).save();
        offer().product(laptop().name("같은것").save()).price(2_000_000L).save();
        offer().product(laptop().name("비싼것").save()).price(3_000_000L).save();

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
        offer().product(laptop().name("아래").save()).price(1_000_000L).save();
        offer().product(laptop().name("안").save()).price(2_000_000L).save();
        offer().product(laptop().name("위").save()).price(3_000_000L).save();

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(
            new LaptopSearchCondition(null, null, null, null, null, null, 1_500_000L, 2_500_000L));

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("안");
    }

    @Test
    void 가격_조건은_판매_중인_오퍼의_최저가를_기준으로_한다() {
        // given
        Laptop laptop = laptop().name("노트북").save();
        offer().product(laptop).price(3_000_000L).status(OfferStatus.ON_SALE).save();
        offer().product(laptop).price(2_000_000L).status(OfferStatus.ON_SALE).save();
        offer().product(laptop).price(1_000_000L).status(OfferStatus.SOLD_OUT).save();

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(
            new LaptopSearchCondition(null, null, null, null, null, null, null, 1_500_000L));

        // then
        assertThat(found.getContent()).isEmpty();
    }

    @Test
    void 조건을_여러_개_지정할_때_조회하면_모두_만족하는_것만_반환한다() {
        // given
        offer().product(laptop().brand("LG").name("그램 16").os(Os.MAC)
            .cpuScore(20000).memoryGb(32).storageGb(1024).save()).save();
        offer().product(laptop().brand("LG").name("그램 저사양").os(Os.MAC)
            .cpuScore(5000).memoryGb(32).storageGb(1024).save()).save();
        offer().product(laptop().brand("LG").name("울트라 PC").os(Os.MAC)
            .cpuScore(20000).memoryGb(32).storageGb(1024).save()).save();
        offer().product(laptop().brand("Apple").name("그램과 비슷한 것").os(Os.MAC)
            .cpuScore(20000).memoryGb(32).storageGb(1024).save()).save();
        offer().product(laptop().brand("LG").name("그램 윈도우")
            .cpuScore(20000).memoryGb(32).storageGb(1024).save()).save();

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(
            new LaptopSearchCondition(Os.MAC, 10000, 16, 512, "그램", "LG", null, null));

        // then
        assertThat(found.getContent()).extracting(LaptopSummaryResponse::name).containsExactly("그램 16");
    }

    @Test
    void 페이지를_넘겨_조회하면_이어지는_결과와_hasNext를_반환한다() {
        // given
        offer().product(laptop().name("1").save()).save();
        offer().product(laptop().name("2").save()).save();
        offer().product(laptop().name("3").save()).save();

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
        offer().product(laptop().name("유일").save()).save();

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(new LaptopSearchCondition(Os.MAC, null, null, null, null, null, null, null));

        // then
        assertThat(found.getContent()).isEmpty();
        assertThat(found.hasNext()).isFalse();
    }

    @Test
    void 페이지가_범위를_넘을_때_조회하면_빈_결과를_반환한다() {
        // given
        offer().product(laptop().name("1").save()).save();
        offer().product(laptop().name("2").save()).save();

        // when
        Slice<LaptopSummaryResponse> found = findLaptops(5, 10);

        // then
        assertThat(found.getContent()).isEmpty();
        assertThat(found.hasNext()).isFalse();
    }

    @Test
    void 판매_중인_오퍼가_없을_때_조회하면_결과에서_제외된다() {
        // given
        offer().product(laptop().name("판매중").save()).save();
        laptop().name("오퍼없음").save();
        Laptop soldOut = laptop().name("품절").save();
        offer().product(soldOut).price(1_000_000L).status(OfferStatus.SOLD_OUT).save();
        offer().product(soldOut).price(1_000_000L).status(OfferStatus.DISCONTINUED).save();

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
        flushAndClear();
        return laptopRepository.findAllByCondition(condition, PageRequest.of(page, size, Sort.by("id")));
    }
}
