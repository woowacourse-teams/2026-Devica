package com.wrb.devica.recommendation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import com.wrb.devica.common.E2ETest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecommendationE2ETest extends E2ETest {

    private static final String PATH = "/api/usage-purposes/BACKEND_DEVELOPMENT/recommendation";

    // UC-03: 답변 전에는 OS 별 기본 권장 사양을 본다
    @Test
    void 권장_사양을_조회하면_Mac_과_Windows_기본안을_받는다() {
        given().log().all()
            .when().get(PATH)
            .then().log().all()
            .statusCode(200)
            .body("specs.size()", is(2))
            .body("specs[0].items.find { it.code == 'OS' }.displayValue", is("Mac"))
            .body("specs[0].items.find { it.code == 'CPU_TIER' }.displayValue", is("M 칩"))
            .body("specs[0].items.find { it.code == 'MEMORY' }.displayValue", is("24GB"))
            .body("specs[1].items.find { it.code == 'OS' }.displayValue", is("Windows"));
    }

    // UC-04: 답변을 보내면 조정한 사양과 근거를 받는다
    @Test
    void 답변을_보내면_조정한_사양과_근거를_받는다() {
        given().log().all()
            .contentType(ContentType.JSON)
            .body(Map.of("answers", Map.of(
                "PREFERRED_OS", List.of("WINDOWS"),
                "PROGRAMMING_LANGUAGE", List.of("JAVA_FAMILY"),
                "IDE", List.of("JETBRAINS"),
                "USAGE_PERIOD", List.of("FIVE_PLUS_YEARS"))))
            .when().post(PATH)
            .then().log().all()
            .statusCode(200)
            .body("specs.size()", is(1))
            .body("specs[0].items.find { it.code == 'OS' }.value", is("WINDOWS"))
            .body("specs[0].items.find { it.code == 'CPU_TIER' }.value", is("H"))
            .body("specs[0].items.find { it.code == 'MEMORY' }.displayValue", is("40GB"))
            .body("specs[0].items.find { it.code == 'MEMORY' }.reasons",
                hasItem("오래 사용할 계획이 상향 판단을 보강했습니다."));
    }
}
