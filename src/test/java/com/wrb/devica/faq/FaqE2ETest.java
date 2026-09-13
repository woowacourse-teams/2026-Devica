package com.wrb.devica.faq;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import com.wrb.devica.common.E2ETest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FaqE2ETest extends E2ETest {

    private static final String PATH = "/api/home/faqs";

    @Autowired
    private FaqRepository faqRepository;

    @Test
    void 홈페이지_FAQ를_조회하면_공개된_FAQ_목록을_반환한다() {
        // given
        faqRepository.save(Faq.home("how-to-use", "Devica는 어떻게 이용하나요?", "답변", true, 2));
        faqRepository.save(Faq.home("why-devica", "Devica는 어떤 서비스인가요?", "답변", true, 1));
        faqRepository.save(Faq.home("private-faq", "공개되지 않은 질문", "답변", false, 0));

        // when & then
        given()
            .when().get(PATH)
            .then().statusCode(200)
            .body("size()", is(2))
            .body("slug", contains("why-devica", "how-to-use"))
            .body("question", contains("Devica는 어떤 서비스인가요?", "Devica는 어떻게 이용하나요?"));
    }
}
