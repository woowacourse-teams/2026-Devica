package com.wrb.devica.faq;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import com.wrb.devica.category.ProductCategory;
import com.wrb.devica.category.ProductCategoryCode;
import com.wrb.devica.category.ProductCategoryRepository;
import com.wrb.devica.common.E2ETest;
import com.wrb.devica.purpose.UsagePurpose;
import com.wrb.devica.purpose.UsagePurposeCode;
import com.wrb.devica.purpose.UsagePurposeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FaqE2ETest extends E2ETest {

    private static final String PATH = "/api/home/faqs";
    private static final String LAPTOP = "LAPTOP";
    private static final String BACKEND = "BACKEND_DEVELOPMENT";

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private UsagePurposeRepository purposeRepository;

    @Autowired
    private FaqRepository faqRepository;

    private ProductCategory category;

    private UsagePurpose usagePurpose;

    @BeforeEach
    void setUp() {
        category = productCategoryRepository.save(
            ProductCategory.from(ProductCategoryCode.LAPTOP)
        );
        usagePurpose = purposeRepository.save(
            UsagePurpose.of(category, UsagePurposeCode.BACKEND_DEVELOPMENT)
        );
    }

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

    @Test
    void 선택한_제품_종류와_사용_목적의_공개_FAQ를_노출_순서대로_반환한다() {
        // given
        Faq first = Faq.forUsagePurpose(
            usagePurpose, "first", "첫 번째 질문", "답변", true, 1
        );
        Faq second = Faq.forUsagePurpose(
            usagePurpose, "second", "두 번째 질문", "답변", true, 2
        );
        Faq homeFaq = Faq.home(
            "home", "홈 질문", "답변", true, 0
        );
        Faq privateFaq = Faq.forUsagePurpose(
            usagePurpose, "private", "비공개 질문", "답변", false, 0
        );

        faqRepository.save(second);
        faqRepository.save(first);
        faqRepository.save(homeFaq);
        faqRepository.save(privateFaq);

        // when & then
        given()
            .pathParam("categoryCode", LAPTOP)
            .pathParam("purposeCode", BACKEND)
            .when()
            .get("/api/product-categories/{categoryCode}/usage-purposes/{purposeCode}/faqs")
            .then().statusCode(200)
            .body("size()", is(2))
            .body("slug", contains("first", "second"));
    }

    @Test
    void 연결된_FAQ가_없으면_빈_목록을_반환한() {
        given()
            .pathParam("categoryCode", LAPTOP)
            .pathParam("purposeCode", BACKEND)
            .when()
            .get("/api/product-categories/{categoryCode}/usage-purposes/{purposeCode}/faqs")
            .then().statusCode(200)
            .body("size()", is(0));
    }
}
