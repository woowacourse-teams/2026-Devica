package com.wrb.devica.recommendation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.wrb.devica.common.E2ETest;
import org.junit.jupiter.api.Test;

class SurveyE2ETest extends E2ETest {

    @Test
    void 설문을_조회하면_단계별로_묶인_질문을_받는다() {
        given().log().all()
            .when().get("/api/usage-purposes/BACKEND_DEVELOPMENT/survey")
            .then().log().all()
            .statusCode(200)
            .body("steps.size()", is(10))
            .body("steps[0].title", is("현재 사용하는 노트북 사양을 알려주세요"))
            .body("steps[0].layout", is("CURRENT_SPEC"))
            .body("steps[0].questions.code", contains(
                "CURRENT_OS", "CURRENT_MAC_CPU", "CURRENT_WINDOWS_CPU", "CURRENT_MEMORY", "CURRENT_STORAGE"))
            .body("steps[0].questions[0].showWhen", nullValue())
            .body("steps[0].questions[1].label", is("PROCESSOR"))
            .body("steps[0].questions[1].showWhen.question", is("CURRENT_OS"))
            .body("steps[0].questions[1].showWhen.anyOf", contains("MACOS"))
            .body("steps[2].questions[0].input", is("MULTI"))
            .body("steps[2].questions[0].exclusive", is("UNDECIDED"))
            .body("steps[3].title", is("개발 도구를 알려주세요"))
            .body("steps[3].questions.code", contains("IDE", "AI_CODING_TOOL"));
    }
}
