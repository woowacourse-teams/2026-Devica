package com.wrb.devica.product;

import static com.wrb.devica.fixture.CpuFixture.cpu;
import static com.wrb.devica.fixture.LaptopFixture.laptop;
import static com.wrb.devica.fixture.ProductOfferFixture.offer;
import static com.wrb.devica.fixture.ProductOfferFixture.onSaleOffer;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import com.wrb.devica.category.ProductCategory;
import com.wrb.devica.category.ProductCategoryCode;
import com.wrb.devica.category.ProductCategoryRepository;
import com.wrb.devica.common.E2ETest;
import com.wrb.devica.fixture.LaptopFixture.LaptopBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LaptopE2ETest extends E2ETest {

    private static final String PATH = "/api/laptops";

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private CpuRepository cpuRepository;

    @Autowired
    private LaptopRepository laptopRepository;

    @Autowired
    private ProductOfferRepository productOfferRepository;

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
            .body("content[0].cpuName", is("Intel Core Ultra 7 255H"))
            .body("content[0].minPrice", is(2_850_000));
    }

    // UC-07/UC-08: 검색어와 가격·사양·OS·브랜드 조건을 적용할 수 있고, 조건을 지정하면 조건을 만족하는 제품만 반환한다
    @Test
    void 조건을_지정하면_전부_만족하는_노트북만_받는다() {
        onSaleLaptop(laptop().brand("LG").name("대상 프로"), 2_500_000L);

        // 조건을 하나씩만 어긋나게 둔다
        onSaleLaptop(laptop().brand("LG").name("os 프로").os(Os.MAC), 2_500_000L);
        addOnSaleOffer(laptopRepository.save(laptop().category(category).cpu(cpu).brand("LG").name("cpu 프로").cpu(cpuRepository.save(cpu().score(5000).build())).build()), 2_500_000L);
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
            .queryParam("cpuScore", 10000)
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
        // 가성비: 쌈 0.8 ÷ 100만, 비쌈 2.0 ÷ 300만, 중간 0.8 ÷ 200만
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
            .body("content.name", contains("쌈", "비쌈", "중간"));

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
        // 가성비: 비쌈 2.0 ÷ 300만, 쌈 0.8 ÷ 100만. id 순이면 비쌈이 앞선다
        onSaleLaptop(laptop().name("비쌈").memoryGb(32).storageGb(1024), saveCpu(40_000), 3_000_000L);
        onSaleLaptop(laptop().name("쌈"), 1_000_000L);

        // when & then
        given()
            .when().get(PATH)
            .then().statusCode(200)
            .body("content.name", contains("쌈", "비쌈"));
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
        productOfferRepository.save(onSaleOffer(laptop, 2_850_000L));
        productOfferRepository.save(offer()
            .product(laptop)
            .price(2_500_000L).status(OfferStatus.SOLD_OUT)
            .build());

        // when & then
        given()
            .when().get(PATH + "/" + laptop.getId())
            .then().statusCode(200)
            .body("name", is("gram Pro 16"))
            .body("offers.price", contains(2_850_000, 2_990_000))
            .body("offers[0].purchaseUrl", is("https://example.com/" + laptop.getCode()));
    }

    private void addOnSaleOffer(Product product, long price) {
        productOfferRepository.save(onSaleOffer(product, price));
    }

    private Cpu saveCpu() {
        return cpuRepository.save(cpu().build());
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
        return laptop;
    }

    private Cpu saveCpu(int score) {
        return cpuRepository.save(cpu().score(score).build());
    }
}
