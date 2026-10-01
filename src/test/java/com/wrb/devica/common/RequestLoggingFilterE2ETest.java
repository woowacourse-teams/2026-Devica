package com.wrb.devica.common;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.wrb.devica.faq.domain.Faq;
import com.wrb.devica.faq.repository.FaqRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "logging.structured.format.console=ecs")
class RequestLoggingFilterE2ETest extends E2ETest {

    private static final String REQUEST_LOG = "요청 처리 완료";

    @Autowired
    private FaqRepository faqRepository;

    @Test
    void 응답_헤더의_trace_id와_경로_패턴을_요청_로그에_남긴다(CapturedOutput output) {
        // given
        Faq faq = faqRepository.save(Faq.service("how-to-use", "Devica는 어떻게 이용하나요?", "답변", true, 1));

        // when
        String traceId = given()
            .when().get("/api/faqs/{slug}", faq.getSlug())
            .then().extract().header("X-Trace-Id");

        // then
        List<String> requestLogs = findRequestLogs(output);

        await().untilAsserted(() -> assertThat(requestLogs)
            .anySatisfy(log -> assertThat(log).contains(
                "\"trace\":{\"id\":\"" + traceId + "\"}",
                "\"route\":\"/api/faqs/{slug}\"",
                "\"path\":\"/api/faqs/how-to-use\"")));
    }

    @Test
    void 요청마다_다른_trace_id를_붙인다() {
        // given & when
        String first = given().when().get("/api/faqs").then().extract().header("X-Trace-Id");
        String second = given().when().get("/api/faqs").then().extract().header("X-Trace-Id");

        // then
        assertThat(first).isNotBlank().isNotEqualTo(second);
    }

    @Test
    void 헬스체크_경로는_요청_로그를_남기지_않는다(CapturedOutput output) {
        // given & when
        given().when().get("/readyz");
        given().when().get("/api/faqs");

        // then
        // 뒤의 요청 로그가 찍힌 뒤에 보면 앞의 헬스체크 요청도 이미 처리가 끝난 상태다.
        await().untilAsserted(() -> assertThat(findRequestLogs(output))
            .anySatisfy(log -> assertThat(log).contains("\"path\":\"/api/faqs\"")));
        assertThat(findRequestLogs(output)).noneSatisfy(log -> assertThat(log).contains("/readyz"));
    }

    private List<String> findRequestLogs(CapturedOutput output) {
        return output.getOut().lines()
            .filter(line -> line.contains(REQUEST_LOG))
            .toList();
    }
}
