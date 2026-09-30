package com.wrb.devica.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wrb.devica.common.exception.BusinessErrorCode;
import com.wrb.devica.common.exception.BusinessException;
import com.wrb.devica.faq.controller.FaqController;
import com.wrb.devica.faq.service.FaqService;
import com.wrb.devica.product.controller.ProductController;
import com.wrb.devica.product.service.ProductOfferService;
import com.wrb.devica.product.service.ProductService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(controllers = {FaqController.class, ProductController.class}, properties = "logging.structured.format.console=ecs")
class RequestLoggingFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FaqService faqService;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private ProductOfferService productOfferService;

    @Test
    void 처리하지_못한_예외는_trace_id와_error_type이_담긴_ERROR_로그로_남는다(CapturedOutput output) throws Exception {
        given(faqService.findPublishedServiceFaqs()).willThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/api/faqs")).andExpect(status().isInternalServerError());

        String errorLog = findLine(output, "\"level\":\"ERROR\"");
        String requestLog = findLine(output, "요청 처리 완료");
        String traceId = errorLog.replaceAll(".*\"trace\":\\{\"id\":\"([0-9a-f]{32})\"\\}.*", "$1");
        assertThat(errorLog).contains("\"error\":{\"type\":\"java.lang.IllegalStateException\"");
        assertThat(requestLog).contains("\"trace\":{\"id\":\"" + traceId + "\"}", "\"status_code\":500");
    }

    @Test
    void 요청마다_다른_trace_id를_붙인다(CapturedOutput output) throws Exception {
        given(faqService.findPublishedServiceFaqs()).willReturn(List.of());

        mockMvc.perform(get("/api/faqs"));
        mockMvc.perform(get("/api/faqs"));

        List<String> traceIds = output.getOut().lines()
            .filter(line -> line.contains("요청 처리 완료"))
            .map(line -> line.replaceAll(".*\"trace\":\\{\"id\":\"([0-9a-f]{32})\"\\}.*", "$1"))
            .toList();
        assertThat(traceIds).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    void 잘못된_요청은_예외_메시지_없이_error_type만_WARN으로_남긴다(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/faqs")).andExpect(status().isMethodNotAllowed());

        String warnLog = findLine(output, "\"level\":\"WARN\",\"logger\":\"com.wrb.devica.common.exception.GlobalExceptionHandler\"");
        assertThat(warnLog)
            .contains("\"message\":\"잘못된 요청\"", "\"error\":{\"type\":\"org.springframework.web.HttpRequestMethodNotSupportedException\"}")
            .doesNotContain("stack_trace");
    }

    @Test
    void 비즈니스_예외는_error_code를_WARN으로_남긴다(CapturedOutput output) throws Exception {
        given(faqService.findPublishedFaqBySlug("none")).willThrow(new BusinessException(BusinessErrorCode.FAQ_NOT_FOUND));

        mockMvc.perform(get("/api/faqs/none")).andExpect(status().isNotFound());

        assertThat(findLine(output, "\"level\":\"WARN\",\"logger\":\"com.wrb.devica.common.exception.GlobalExceptionHandler\"")).contains("\"error\":{\"code\":\"FAQ_NOT_FOUND\"}");
    }

    @Test
    void 응답_헤더의_trace_id와_경로_패턴을_요청_로그에_남긴다(CapturedOutput output) throws Exception {
        given(faqService.findPublishedFaqBySlug("none")).willThrow(new BusinessException(BusinessErrorCode.FAQ_NOT_FOUND));

        String traceId = mockMvc.perform(get("/api/faqs/none"))
            .andReturn().getResponse().getHeader("X-Trace-Id");

        assertThat(findLine(output, "요청 처리 완료"))
            .contains("\"trace\":{\"id\":\"" + traceId + "\"}", "\"route\":\"/api/faqs/{slug}\"");
    }

    @Test
    void 없는_경로는_WARN을_남기지_않는다(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/wp-admin")).andExpect(status().isNotFound());

        assertThat(output.getOut()).doesNotContain("\"logger\":\"com.wrb.devica.common.exception.GlobalExceptionHandler\"");
    }

    @Test
    void 검증_실패는_error_type과_제약_조건_문구를_WARN으로_남긴다(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/product-categories/laptop/products").param("size", "0"))
            .andExpect(status().isBadRequest());

        assertThat(findLine(output, "\"level\":\"WARN\",\"logger\":\"com.wrb.devica.common.exception.GlobalExceptionHandler\""))
            .contains("\"message\":\"요청 값 검증 실패\"", "\"type\":\"org.springframework.web.bind.MethodArgumentNotValidException\"",
                "\"message\":\"페이지 크기는 1 이상이어야 합니다.\"");
    }

    private String findLine(CapturedOutput output, String keyword) {
        return output.getOut().lines()
            .filter(line -> line.contains(keyword))
            .findFirst()
            .orElseThrow();
    }
}
