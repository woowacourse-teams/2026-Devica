package com.wrb.devica.recommendation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import com.wrb.devica.common.E2ETest;
import org.junit.jupiter.api.Test;

class RecommendationE2ETest extends E2ETest {

    private static final String PATH = "/api/usage-purposes/BACKEND_DEVELOPMENT/recommendation";

    // UC-03: 답변 전에는 OS 별 기본 권장 사양을 본다
    @Test
    void 답변_없이_조회하면_Mac_과_Windows_기본안을_받는다() {
        given().log().all()
            .when().get(PATH)
            .then().log().all()
            .statusCode(200)
            .body("specs.size()", is(2))
            .body("specs[0].items.find { it.code == 'OS' }.displayValue", is("Mac"))
            .body("specs[0].items.find { it.code == 'REQUIRED_CPU' }.displayValue", is("M 칩"))
            .body("specs[0].items.find { it.code == 'MEMORY' }.displayValue", is("16GB"))
            .body("specs[1].items.find { it.code == 'OS' }.displayValue", is("Windows"))
            .body("specs.preferred", contains(false, false));
    }

    // UC-04: 답변을 파라미터로 보내면 조정한 사양과 근거를 받는다. 선호 OS 는 기본으로 보여줄 권장안만 정한다
    // 사양 값은 정의서 규칙이 정하므로 여기서 박아 두지 않고 RecommendationServiceTest 의 스냅숏이 지킨다
    @Test
    void 답변을_보내면_조정한_사양과_근거를_받는다() {
        given().log().all()
            .queryParam("PREFERRED_OS", "WINDOWS")
            .queryParam("PROGRAMMING_LANGUAGE", "JAVA_FAMILY")
            .queryParam("IDE", "JETBRAINS")
            .queryParam("DEV_ENVIRONMENT_SETUP", "DOCKER_MANY")
            .queryParam("BUILD_WAIT", "OFTEN")
            .when().get(PATH)
            .then().log().all()
            .statusCode(200)
            .body("specs.size()", is(2))
            .body("specs.preferred", contains(false, true))
            .body("specs[1].items.find { it.code == 'OS' }.value", is("WINDOWS"))
            .body("specs[1].items.code", hasItems("REQUIRED_CPU", "MEMORY", "STORAGE"))
            .body("specs[1].items.find { it.code == 'MEMORY' }.displayValue", not(blankOrNullString()))
            .body("specs[1].items.find { it.code == 'MEMORY' }.reasons", hasItem(not(blankOrNullString())));
    }
}
