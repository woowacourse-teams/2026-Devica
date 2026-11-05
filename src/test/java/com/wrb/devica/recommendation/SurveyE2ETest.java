package com.wrb.devica.recommendation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import com.wrb.devica.common.E2ETest;
import com.wrb.devica.recommendation.domain.Question;
import com.wrb.devica.recommendation.domain.Step;
import com.wrb.devica.recommendation.domain.Survey;
import com.wrb.devica.recommendation.service.RecommendationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SurveyE2ETest extends E2ETest {

    private static final String PURPOSE = "BACKEND_DEVELOPMENT";

    @Autowired
    private RecommendationService recommendationService;

    @Test
    void 설문을_조회하면_정의서의_질문을_단계별로_받는다() {
        // given
        Survey survey = recommendationService.findSurvey(PURPOSE);
        List<List<String>> questionCodes = survey.steps().stream()
            .map(step -> step.questions().stream().map(Question::code).toList())
            .toList();
        List<String> layouts = survey.steps().stream().map(Step::layout).toList();

        // when & then
        given().log().all()
            .when().get("/api/usage-purposes/" + PURPOSE + "/survey")
            .then().log().all()
            .statusCode(200)
            .body("steps.questions.code", is(questionCodes))
            .body("steps.layout", is(layouts))
            .body("steps.questions.flatten().title", everyItem(not(blankOrNullString())))
            .body("steps.questions.flatten().input", everyItem(in(List.of("SINGLE", "MULTI"))))
            .body("steps.questions.flatten().options", everyItem(not(empty())))
            .body("steps.questions.flatten().options.flatten().code", everyItem(not(blankOrNullString())))
            .body("steps.questions.flatten().options.flatten().content", everyItem(not(blankOrNullString())))
            .body("steps.questions.flatten().findAll { it.showWhen != null }.showWhen.question",
                everyItem(not(blankOrNullString())))
            .body("steps.questions.flatten().findAll { it.showWhen != null }.showWhen.anyOf",
                everyItem(not(empty())));
    }
}
