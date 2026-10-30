package com.wrb.devica.product;

import static com.wrb.devica.fixture.CpuFixture.cpu;
import static com.wrb.devica.fixture.LaptopFixture.laptop;
import static com.wrb.devica.fixture.ProductOfferFixture.offer;
import static com.wrb.devica.fixture.ProductOfferFixture.onSaleOffer;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wrb.devica.category.domain.ProductCategory;
import com.wrb.devica.category.domain.ProductCategoryCode;
import com.wrb.devica.category.repository.ProductCategoryRepository;
import com.wrb.devica.common.E2ETest;
import com.wrb.devica.common.MinPriceRefresher;
import com.wrb.devica.fixture.LaptopFixture.LaptopBuilder;
import com.wrb.devica.product.domain.Cpu;
import com.wrb.devica.product.domain.CpuTier;
import com.wrb.devica.product.domain.Laptop;
import com.wrb.devica.product.domain.OfferStatus;
import com.wrb.devica.product.domain.Os;
import com.wrb.devica.product.domain.Product;
import com.wrb.devica.product.domain.ProductOffer;
import com.wrb.devica.product.dto.PriceRefreshTarget;
import com.wrb.devica.product.infra.coupang.CoupangProductSearchClient;
import com.wrb.devica.product.infra.coupang.CoupangPriceRefreshJob;
import com.wrb.devica.product.service.ProductOfferService;
import com.wrb.devica.product.repository.CpuRepository;
import com.wrb.devica.product.repository.LaptopRepository;
import com.wrb.devica.product.repository.ProductOfferRepository;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(MinPriceRefresher.class)
class LaptopE2ETest extends E2ETest {

    private static final String PATH = "/api/product-categories/LAPTOP/products";

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private CpuRepository cpuRepository;

    @Autowired
    private LaptopRepository laptopRepository;

    @Autowired
    private ProductOfferRepository productOfferRepository;

    @Autowired
    private MinPriceRefresher minPriceRefresher;

    @Autowired
    private ProductOfferService productOfferService;

    private ProductCategory category;

    private Cpu cpu;

    @BeforeEach
    void setUpCategory() {
        category = productCategoryRepository.save(ProductCategory.from(ProductCategoryCode.LAPTOP));
        cpu = saveCpu();
    }

    // UC-07: 전체 제품을 조회한다
    @Test
    void 노트북_목록을_조회하면_사양과_최저가를_함께_받는다() {
        // given
        Laptop saved = laptopRepository.save(laptop().category(category)
            .brand("LG")
            .name("gram Pro 16")
            .cpu(cpuRepository.save(cpu().name("Intel Core Ultra 7 255H").coreCount(16).score(20000).build()))
            .memoryGb(32)
            .storageGb(1024)
            .build());

        addOnSaleOffer(saved, 2_990_000L);
        addOnSaleOffer(saved, 2_850_000L);

        // when & then
        given()
            .when().get(PATH)
            .then().statusCode(200)
            .body("content.size()", is(1))
            .body("content[0].name", is("gram Pro 16"))
            .body("content[0].specs.code", contains("OS", "CPU", "MEMORY", "STORAGE"))
            .body("content[0].specs.displayValue", contains("Windows", "Intel Core Ultra 7 255H", "32GB", "1TB"))
            .body("content[0].minPrice", is(2_850_000));
    }

    @Test
    void 목록과_상세에서_이미지_주소만_반환한다() {
        Laptop saved = laptopRepository.save(laptop().category(category).cpu(cpu)
            .imageKey("/devica/products/sample.webp").build());

        String imageUrl = "https://images.example.test/devica/products/sample.webp";

        given()
            .when().get(PATH)
            .then().statusCode(200)
            .body("content[0].imageUrl", is(imageUrl))
            .body("content[0].containsKey('imageKey')", is(false));

        given()
            .when().get("/api/products/" + saved.getId())
            .then().statusCode(200)
            .body("imageUrl", is(imageUrl))
            .body("containsKey('imageKey')", is(false));
    }

    // UC-07/UC-08: 검색어와 가격·사양·OS·브랜드 조건을 적용할 수 있고, 조건을 지정하면 조건을 만족하는 제품만 반환한다
    @Test
    void 조건을_지정하면_전부_만족하는_노트북만_받는다() {
        onSaleLaptop(laptop().brand("LG").name("대상 프로"), 2_500_000L);

        // 조건을 하나씩만 어긋나게 둔다
        onSaleLaptop(laptop().brand("LG").name("os 프로").os(Os.MAC), 2_500_000L);
        addOnSaleOffer(laptopRepository.save(laptop().category(category).cpu(cpu).brand("LG").name("cpu 프로")
            .cpu(cpuRepository.save(cpu().score(5000).build())).build()), 2_500_000L);
        onSaleLaptop(laptop().brand("LG").name("메모리 프로").memoryGb(8), 2_500_000L);
        onSaleLaptop(laptop().brand("LG").name("저장장치 프로").storageGb(256), 2_500_000L);
        onSaleLaptop(laptop().brand("LG").name("싼 프로"), 900_000L);
        onSaleLaptop(laptop().brand("LG").name("비싼 프로"), 5_000_000L);
        onSaleLaptop(laptop().brand("Apple").name("브랜드 프로"), 2_500_000L);
        onSaleLaptop(laptop().brand("LG").name("검색어 불일치"), 2_500_000L);

        // when & then
        given()
            .queryParam("keyword", "프로")
            .queryParam("brand", "LG")
            .queryParam("os", "WINDOWS")
            .queryParam("cpuTier", CpuTier.H)
            .queryParam("memoryGb", 16)
            .queryParam("storageGb", 512)
            .queryParam("minPrice", 1_000_000L)
            .queryParam("maxPrice", 3_000_000L)
            .when().get(PATH)
            .then().statusCode(200)
            .body("content.size()", is(1))
            .body("content[0].name", is("대상 프로"));
    }

    // UC-07: 목록을 나눠 받고 다음 페이지가 있는지 안다
    @Test
    void 페이지를_넘기면_이어지는_노트북과_다음_페이지_여부를_받는다() {
        // given
        onSaleLaptop(laptop().name("첫째"), 1_000_000L);
        onSaleLaptop(laptop().name("둘째"), 1_000_000L);
        onSaleLaptop(laptop().name("셋째"), 1_000_000L);

        // when & then
        given()
            .queryParam("size", 2)
            .when().get(PATH)
            .then().statusCode(200)
            .body("content.name", contains("첫째", "둘째"))
            .body("hasNext", is(true));

        given()
            .queryParam("page", 1)
            .queryParam("size", 2)
            .when().get(PATH)
            .then().statusCode(200)
            .body("content.name", contains("셋째"))
            .body("hasNext", is(false));
    }

    // UC-08: 검색 조건을 초기화한다
    @Test
    void 조건을_빼면_다시_전체를_받는다() {
        // given
        onSaleLaptop(laptop().brand("LG").name("그램"), 2_850_000L);
        onSaleLaptop(laptop().brand("Apple").name("맥북"), 1_890_000L);

        // when & then
        given()
            .queryParam("brand", "LG")
            .when().get(PATH)
            .then().statusCode(200)
            .body("content.size()", is(1));

        given()
            .when().get(PATH)
            .then().statusCode(200)
            .body("content.size()", is(2));
    }

    // UC-07: 결과가 없어도 조회는 성공한 것이다
    @Test
    void 조건에_맞는_노트북이_없으면_빈_목록을_받는다() {
        // given
        onSaleLaptop(laptop().name("그램"), 2_850_000L);

        // when & then
        given()
            .queryParam("minPrice", 9_000_000L)
            .when().get(PATH)
            .then().statusCode(200)
            .body("content.size()", is(0))
            .body("hasNext", is(false));
    }

    // UC-09: 추천순·가격 낮은 순·가격 높은 순으로 정렬한다. 검색 조건을 유지한 채 정렬된 목록을 표시한다.
    @Test
    void 정렬_기준을_고르면_조건을_유지한_채_그_순서대로_받는다() {
        // given
        onSaleLaptop(laptop().brand("LG").name("비쌈").memoryGb(32).storageGb(1024), saveCpu(40_000), 3_000_000L);
        onSaleLaptop(laptop().brand("LG").name("쌈"), 1_000_000L);
        onSaleLaptop(laptop().brand("LG").name("중간"), 2_000_000L);
        onSaleLaptop(laptop().brand("Apple").name("브랜드 불일치"), 500_000L);

        // when & then
        given()
            .queryParam("brand", "LG")
            .queryParam("sort", "RECOMMENDED")
            .when().get(PATH)
            .then().statusCode(200)
            .body("content.name", contains("비쌈", "쌈", "중간"));

        given()
            .queryParam("brand", "LG")
            .queryParam("sort", "PRICE_ASC")
            .when().get(PATH)
            .then().statusCode(200)
            .body("content.name", contains("쌈", "중간", "비쌈"));

        given()
            .queryParam("brand", "LG")
            .queryParam("sort", "PRICE_DESC")
            .when().get(PATH)
            .then().statusCode(200)
            .body("content.name", contains("비쌈", "중간", "쌈"));
    }

    // UC-09: 정렬 기준을 고르지 않으면 추천순으로 정렬한다
    @Test
    void 정렬_기준을_고르지_않으면_추천순으로_받는다() {
        // given
        // 사양: 사양높음이 위. id 순이면 사양낮음이 앞선다
        onSaleLaptop(laptop().name("사양낮음"), 1_000_000L);
        onSaleLaptop(laptop().name("사양높음").memoryGb(32).storageGb(1024), saveCpu(40_000), 3_000_000L);

        // when & then
        given()
            .when().get(PATH)
            .then().statusCode(200)
            .body("content.name", contains("사양높음", "사양낮음"));
    }

    // UC-07: 판매 중인 구매처가 없는 제품도 목록에 넣고 가격은 비워둔다
    @Test
    void 살_수_없는_노트북도_목록에_나오고_최저가는_비어_있다() {
        // given
        onSaleLaptop(laptop().name("판매중"));

        laptopOf(laptop().name("오퍼없음"));

        productOfferRepository.save(offer()
            .product(laptopOf(laptop().name("품절")))
            .status(OfferStatus.SOLD_OUT).build());

        // when & then
        given()
            .when().get(PATH)
            .then().statusCode(200)
            .body("content.name", contains("판매중", "오퍼없음", "품절"))
            .body("content.minPrice", contains(1_000_000, null, null));
    }

    @Test
    void 상위_검색_후보에_없어도_기존_가격과_확인일과_목록_상세_노출을_유지한다() {
        // given
        Laptop laptop = laptopOf(laptop().name("검색에서빠진상품"));
        ProductOffer offer = productOfferRepository.save(ProductOffer.builder().productId(laptop.getId()).name("쿠팡")
            .externalItemId("56").price(1_000_000L).checkedAt(LocalDate.of(2026, 10, 25))
            .purchaseUrl("https://www.coupang.com/vp/products/999?vendorItemId=56")
            .status(OfferStatus.ON_SALE).build());
        minPriceRefresher.refresh(laptop.getId());
        CoupangProductSearchClient client = mock(CoupangProductSearchClient.class);
        List<CoupangProductSearchClient.Product> candidates = IntStream.rangeClosed(57, 66)
            .mapToObj(id -> new CoupangProductSearchClient.Product("999", "다른상품", 900_000L,
                "https://www.coupang.com/vp/products/999?vendorItemId=" + id)).toList();
        when(client.search(laptop.getCode())).thenReturn(candidates);
        Clock clock = Clock.fixed(Instant.parse("2026-10-26T16:00:00Z"), ZoneId.of("Asia/Seoul"));
        var job = new CoupangPriceRefreshJob(client, productOfferService, clock, "https://devica.example");

        // when
        var summary = job.refreshPrices();

        // then
        assertThat(summary).isEqualTo(new CoupangPriceRefreshJob.Summary(0, 0, 1, 0, 0, 0, 0));
        ProductOffer unchanged = productOfferRepository.findById(offer.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(OfferStatus.ON_SALE);
        assertThat(unchanged.getPrice()).isEqualTo(1_000_000L);
        assertThat(unchanged.getCheckedAt()).isEqualTo(LocalDate.of(2026, 10, 25));
        given().when().get(PATH).then().statusCode(200).body("content.size()", is(1))
            .body("content[0].minPrice", is(1_000_000));
        given().when().get("/api/products/" + laptop.getId()).then().statusCode(200)
            .body("offers[0].price", is(1_000_000)).body("offers[0].checkedAt", is("2026-10-25"));
    }

    @Test
    void 숨김과_복구가_실제_목록과_상세_API에_반영된다() {
        // given
        Laptop laptop = laptopOf(laptop().name("재확인대상"));
        ProductOffer offer = productOfferRepository.save(ProductOffer.builder().productId(laptop.getId()).name("쿠팡")
            .externalItemId("56").price(1_000_000L).checkedAt(LocalDate.of(2026, 10, 25))
            .purchaseUrl("https://www.coupang.com/vp/products/999?vendorItemId=56")
            .status(OfferStatus.ON_SALE).build());
        minPriceRefresher.refresh(laptop.getId());
        PriceRefreshTarget target = new PriceRefreshTarget(offer.getId(), laptop.getId(), laptop.getCode(), "56",
            offer.getPurchaseUrl(), offer.getPrice(), offer.getStatus());

        // when
        productOfferService.hideCoupangOffer(target, LocalDate.of(2026, 10, 26));

        // then
        given().when().get(PATH).then().statusCode(200).body("content.size()", is(0));
        given().when().get("/api/products/" + laptop.getId()).then().statusCode(200)
            .body("name", is("재확인대상")).body("offers", is(List.of()));

        // given
        PriceRefreshTarget hidden = productOfferService.findCoupangPriceRefreshTargets().getFirst();

        // when
        productOfferService.refreshCoupangPrice(hidden, 900_000L, LocalDate.of(2026, 10, 27));

        // then
        given().when().get(PATH).then().statusCode(200)
            .body("content[0].name", is("재확인대상")).body("content[0].minPrice", is(900_000));
        given().when().get("/api/products/" + laptop.getId()).then().statusCode(200)
            .body("offers[0].price", is(900_000)).body("offers[0].checkedAt", is("2026-10-27"));
    }

    // UC-11: 가격을 확인하고 외부 구매처로 이동할 수 있다
    @Test
    void 상세를_조회하면_판매_중인_판매처를_싼_순으로_받는다() {
        // given
        Laptop laptop = laptopRepository.save(laptop().category(category)
            .brand("LG")
            .name("gram Pro 16")
            .cpu(cpuRepository.save(cpu().name("Intel Core Ultra 7 255H").coreCount(16).score(20000).build()))
            .memoryGb(32)
            .storageGb(1024)
            .build());

        productOfferRepository.save(onSaleOffer(laptop, 2_990_000L));
        productOfferRepository.save(offer()
            .product(laptop)
            .price(2_850_000L).checkedAt(LocalDate.of(2026, 8, 19))
            .build());
        productOfferRepository.save(offer()
            .product(laptop)
            .price(2_500_000L).status(OfferStatus.SOLD_OUT)
            .build());
        productOfferRepository.save(offer()
            .product(laptop)
            .price(2_000_000L).status(OfferStatus.HIDDEN)
            .build());

        // when & then
        given()
            .when().get("/api/products/" + laptop.getId())
            .then().statusCode(200)
            .body("name", is("gram Pro 16"))
            .body("specs.code", contains("OS", "CPU", "MEMORY", "STORAGE", "CPU_CORE", "SCREEN_SIZE", "WEIGHT"))
            .body("offers.price", contains(2_850_000, 2_990_000))
            .body("offers[0].purchaseUrl", is("https://example.com/" + laptop.getCode()))
            .body("offers.checkedAt", contains("2026-08-19", null));
    }

    // UC-08: CPU 필터 선택지는 서버의 등급을 쓴다. 등급을 바꿔도 화면을 고치지 않는다
    @Test
    void CPU_등급을_조회하면_OS_별로_낮은_등급부터_받는다() {
        given()
            .when().get("/api/cpu-tiers")
            .then().statusCode(200)
            .body("code", contains("U", "P_HS", "H", "HX", "BASIC", "PRO", "MAX"))
            .body("[0].name", is("저전력 Core Ultra 5 / Ryzen 5"))
            .body("[4].os", is("MAC"));
    }

    // UC-08: 브랜드 필터 선택지는 보고 있는 OS 에 있는 브랜드만 준다
    @Test
    void OS를_지정해_브랜드를_조회하면_그_OS의_브랜드만_받는다() {
        // given
        laptopOf(laptop().brand("Apple").os(Os.MAC));
        laptopOf(laptop().brand("LG").os(Os.WINDOWS));
        laptopOf(laptop().brand("ASUS").os(Os.WINDOWS));

        // when & then
        given()
            .queryParam("os", "WINDOWS")
            .when().get("/api/product-categories/LAPTOP/brands")
            .then().statusCode(200)
            .body("", contains("ASUS", "LG"));
    }

    // UC-11: 판매처가 없어도 제품 정보는 볼 수 있다
    @Test
    void 판매_중인_판매처가_없어도_상세를_받는다() {
        // given
        Laptop laptop = laptopOf(laptop().name("판매처없음"));

        // when & then
        given()
            .when().get("/api/products/" + laptop.getId())
            .then().statusCode(200)
            .body("name", is("판매처없음"))
            .body("offers.size()", is(0));
    }

    private void addOnSaleOffer(Product product, long price) {
        productOfferRepository.save(onSaleOffer(product, price));
        minPriceRefresher.refresh(product.getId());
    }

    // 사양 조건 테스트가 등급 하한을 넘겨야 하므로 기본 CPU 를 H 등급 이상으로 둔다
    private Cpu saveCpu() {
        return cpuRepository.save(cpu().score(CpuTier.H.getMinScore()).build());
    }

    private Laptop onSaleLaptop(LaptopBuilder builder) {
        long defaultPrice = 1_000_000L;
        return onSaleLaptop(builder, defaultPrice);
    }

    private Laptop onSaleLaptop(LaptopBuilder builder, long price) {
        return onSaleLaptop(builder, cpu, price);
    }

    private Laptop laptopOf(LaptopBuilder builder) {
        return laptopRepository.save(builder.category(category).cpu(cpu).build());
    }

    private Laptop onSaleLaptop(LaptopBuilder builder, Cpu cpu, long price) {
        Laptop laptop = laptopRepository.save(builder.category(category).cpu(cpu).build());
        productOfferRepository.save(onSaleOffer(laptop, price));
        minPriceRefresher.refresh(laptop.getId());
        return laptop;
    }

    private Cpu saveCpu(int score) {
        return cpuRepository.save(cpu().score(score).build());
    }
}
