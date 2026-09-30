package com.wrb.devica.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 요청마다 trace.id 를 MDC 와 응답 헤더에 넣고, 요청이 끝나면 요청·응답 로그를 한 줄 남긴다.
 * 다른 필터의 로그에도 trace.id 가 붙도록 가장 먼저 실행한다.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "trace.id";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final Set<String> HEALTH_CHECK_PATHS = Set.of("/readyz", "/livez");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(TRACE_ID, traceId);
        // 사용자가 오류를 제보할 때 이 값으로 로그를 찾는다.
        response.setHeader(TRACE_ID_HEADER, traceId);
        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            // 쿼리 문자열과 헤더는 남기지 않는다. 민감정보가 섞일 수 있는 곳이다.
            log.atInfo()
                .addKeyValue("http.request.method", request.getMethod())
                .addKeyValue("url.path", request.getRequestURI())
                // 경로 변수 자리를 {id} 처럼 남긴 매핑 패턴이라 API 별로 묶어 볼 수 있다. 없는 경로는 /** 로 남는다.
                .addKeyValue("http.route", request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
                .addKeyValue("http.response.status_code", response.getStatus())
                .addKeyValue("event.duration", System.nanoTime() - startedAt)
                .log("요청 처리 완료");
            MDC.remove(TRACE_ID);
        }
    }

    // ALB 가 주기적으로 호출하는 헬스체크 경로라 요청 로그를 덮지 않도록 제외한다.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HEALTH_CHECK_PATHS.contains(request.getRequestURI());
    }
}
