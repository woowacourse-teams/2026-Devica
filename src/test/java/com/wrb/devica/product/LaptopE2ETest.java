package com.wrb.devica.product;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import com.wrb.devica.category.ProductCategory;
import com.wrb.devica.category.ProductCategoryCode;
import com.wrb.devica.common.E2ETest;
import com.wrb.devica.fixture.CpuFixture;
import com.wrb.devica.fixture.LaptopFixture;
import com.wrb.devica.fixture.ProductOfferFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

class LaptopE2ETest extends E2ETest {

    private static final String PATH = "/api/laptops";
    private ProductCategory category;

    private Cpu cpu;

    @BeforeEach
    void setUpCategory() {
        category = saveWithTransaction(() -> saver.save(ProductCategory.from(ProductCategoryCode.LAPTOP)));
        cpu = saveCpu();
    }

    // UC-07: 전체 제품을 조회한다
    @Test
    void 노트북_목록을_조회하면_사양과_최저가를_함께_받는다() {
        // given
        saveWithTransaction(() -> {
            Laptop saved = saver.save(LaptopFixture.laptop().category(category)
                .brand("LG")
                .name("gram Pro 16")
                .cpu(saver.save(CpuFixture.cpu().name("Intel Core Ultra 7 255H").coreCount(16).score(20000).build()))
                .memoryGb(32)
                .storageGb(1024)
                .build());

            addOnSaleOffer(saved, 2_990_000L);
            addOnSaleOffer(saved, 2_850_000L);

            return saved;
        });

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
        saveWithTransaction(() -> {
            addOnSaleOffer(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).brand("LG").name("대상 프로").build()), 2_500_000L);

            // 조건을 하나씩만 어긋나게 둔다
            addOnSaleOffer(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).brand("LG").name("os 프로").os(Os.MAC).build()), 2_500_000L);
            addOnSaleOffer(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).brand("LG").name("cpu 프로").cpu(saver.save(CpuFixture.cpu().score(5000).build())).build()), 2_500_000L);
            addOnSaleOffer(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).brand("LG").name("메모리 프로").memoryGb(8).build()), 2_500_000L);
            addOnSaleOffer(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).brand("LG").name("저장장치 프로").storageGb(256).build()), 2_500_000L);
            addOnSaleOffer(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).brand("LG").name("싼 프로").build()), 900_000L);
            addOnSaleOffer(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).brand("LG").name("비싼 프로").build()), 5_000_000L);
            addOnSaleOffer(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).brand("Apple").name("브랜드 프로").build()), 2_500_000L);
            addOnSaleOffer(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).brand("LG").name("검색어 불일치").build()), 2_500_000L);

            return null;
        });

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
        saveWithTransaction(() -> {
            addOnSaleOffer(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("첫째").build()), 1_000_000L);
            addOnSaleOffer(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("둘째").build()), 1_000_000L);
            addOnSaleOffer(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("셋째").build()), 1_000_000L);

            return null;
        });

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
        saveWithTransaction(() -> {
            addOnSaleOffer(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).brand("LG").name("그램").build()), 2_850_000L);
            addOnSaleOffer(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).brand("Apple").name("맥북").build()), 1_890_000L);

            return null;
        });

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
        saveWithTransaction(() -> {
            addOnSaleOffer(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("그램").build()), 2_850_000L);

            return null;
        });

        // when & then
        given()
            .queryParam("minPrice", 9_000_000L)
            .when().get(PATH)
            .then().statusCode(200)
            .body("content.size()", is(0))
            .body("hasNext", is(false));
    }

    // UC-07: 살 수 없는 제품은 목록에 넣지 않는다
    @Test
    void 살_수_없는_노트북은_목록에_나오지_않는다() {
        // given
        saveWithTransaction(() -> {
            saver.save(ProductOfferFixture.판매_중_오퍼(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("판매중").build())));

            saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("오퍼없음").build());

            saver.save(ProductOfferFixture.offer()
                .product(saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("품절").build()))
                .status(OfferStatus.SOLD_OUT).build());

            return null;
        });

        // when & then
        given()
            .when().get(PATH)
            .then().statusCode(200)
            .body("content.size()", is(1))
            .body("content[0].name", is("판매중"));
    }

    // UC-11: 가격을 확인하고 외부 구매처로 이동할 수 있다
    @Test
    void 상세를_조회하면_판매_중인_판매처를_싼_순으로_받는다() {
        // given
        Laptop laptop = saveWithTransaction(() -> {
            Laptop saved = saver.save(LaptopFixture.laptop().category(category)
                .brand("LG")
                .name("gram Pro 16")
                .cpu(saver.save(CpuFixture.cpu().name("Intel Core Ultra 7 255H").coreCount(16).score(20000).build()))
                .memoryGb(32)
                .storageGb(1024)
                .build());

            saver.save(ProductOfferFixture.판매_중_오퍼(saved, 2_990_000L));
            saver.save(ProductOfferFixture.판매_중_오퍼(saved, 2_850_000L));
            saver.save(ProductOfferFixture.offer()
                .product(saved)
                .price(2_500_000L).status(OfferStatus.SOLD_OUT)
                .build());

            return saved;
        });

        // when & then
        given()
            .when().get(PATH + "/" + laptop.getId())
            .then().statusCode(200)
            .body("name", is("gram Pro 16"))
            .body("offers.price", contains(2_850_000, 2_990_000))
            .body("offers[0].purchaseUrl", is("https://example.com/" + laptop.getCode()));
    }

    // UC-11: 조회할 수 없는 제품은 목록으로 안내한다
    @Test
    void 살_수_없는_노트북의_상세는_없는_노트북과_똑같이_응답한다() {
        // given
        Laptop soldOut = saveWithTransaction(() -> {
            Laptop saved = saver.save(LaptopFixture.laptop().category(category).cpu(cpu).name("판매종료").build());
            saver.save(ProductOfferFixture.offer().product(saved).status(OfferStatus.DISCONTINUED).build());

            return saved;
        });

        // when
        String notSelling = get404(PATH + "/" + soldOut.getId());
        String notExisting = get404(PATH + "/999999");

        // then
        assertThat(notSelling).isEqualTo(notExisting);
        assertThat(notSelling).contains("조회할 수 없는 노트북입니다.", "LAPTOP_NOT_FOUND");
    }

    private void addOnSaleOffer(Product product, long price) {
        saver.save(ProductOfferFixture.판매_중_오퍼(product, price));
    }

    private String get404(String path) {
        return given()
            .when().get(path)
            .then().statusCode(404)
            .extract().asString();
    }

    private Cpu saveCpu() {
        return saveWithTransaction(() -> saver.save(CpuFixture.cpu().build()));
    }
}
