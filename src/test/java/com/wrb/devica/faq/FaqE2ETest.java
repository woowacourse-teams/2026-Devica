package com.wrb.devica.faq;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import com.wrb.devica.common.E2ETest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FaqE2ETest extends E2ETest {

    private static final String HOME_PATH = "/api/home/faqs";
    private static final String DETAIL_PATH = "/api/faqs/{slug}";

    @Autowired
    private FaqRepository faqRepository;

    @Test
    void 홈페이지_FAQ_목록에서_공개_FAQ를_선택하면_상세를_조회한다() {
        // given
        Faq faq = faqRepository.save(
            Faq.home("how-to-use", "Devica는 어떻게 이용하나요?", "답변", true, 1)
        );

        // when
        String slug = given()
            .when().get(HOME_PATH)
            .then().statusCode(200)
            .body("slug", contains(faq.getSlug()))
            .extract().path("slug[0]");

        // then
        given()
            .pathParam("slug", slug)
            .when()
            .get(DETAIL_PATH)
            .then().statusCode(200)
            .body("answer", is(faq.getAnswer()));
    }
}
