package com.wrb.devica.product.infra.coupang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.wrb.devica.product.dto.PriceRefreshTarget;
import com.wrb.devica.product.service.ProductOfferService;
import com.wrb.devica.product.service.ProductOfferService.PriceRefreshResult;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CoupangPriceRefreshJobTest {

    private static final String SEARCH_URL = "https://api-gateway.coupang.com"
        + "/v2/providers/affiliate_open_api/apis/openapi/v1/products/search";
    private static final String MATCHING_URL =
        "https://link.coupang.com/re/AFFSDP?pageKey=999&itemId=777&vendorItemId=56";
    private static final LocalDate CHECKED_AT = LocalDate.of(2026, 10, 27);
    private static final PriceRefreshTarget FIRST = new PriceRefreshTarget(1L, 10L, "MDH74KH/A", "56");
    private static final PriceRefreshTarget SECOND = new PriceRefreshTarget(2L, 20L, "SECOND", "56");
    private static final PriceRefreshTarget THIRD = new PriceRefreshTarget(3L, 30L, "THIRD", "56");

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-26T16:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final List<Long> pauses = new ArrayList<>();

    private MockRestServiceServer server;
    private CoupangProductSearchClient client;
    private ProductOfferService offers;
    private CoupangPriceRefreshJob job;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CoupangProductSearchClient(builder.build(),
            new CoupangHmacSigner("test-access", "test-secret", clock));
        offers = mock(ProductOfferService.class);
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(FIRST));
        job = new CoupangPriceRefreshJob(client, offers, clock, pauses::add);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        MATCHING_URL,
        "https://www.coupang.com/vp/products/999?vendorItemId=56"
    })
    void vendorItemId가_같으면_페이지와_itemId에_의존하지_않고_한국_날짜로_저장한다(String url) {
        respond(FIRST, product(url, 2_100_000L));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.UPDATED);

        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(1, 0, 0, 0));
        verify(offers).refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT);
        assertThat(pauses).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "not-a-url",
        "https://example.com/vp/products/56?vendorItemId=56",
        "https://www.coupang.com/vp/products/56?itemId=56",
        "https://www.coupang.com/vp/products/56?itemId=56&vendorItemId=57",
        "https://www.coupang.com/vp/products/56?vendorItemId=56&vendorItemId=57"
    })
    void 응답_productId가_같아도_URL에서_같은_vendorItemId를_확인하지_못하면_저장하지_않는다(String url) {
        respond(FIRST, product(url, 2_100_000L));

        assertDeferred();
    }

    @Test
    void 검색_결과가_없으면_기존_가격과_확인_날짜를_저장하지_않는다() {
        respond(FIRST);

        assertDeferred();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void ID가_같아도_유효한_양수_가격이_없으면_저장하지_않는다(Long price) {
        respond(FIRST, product(MATCHING_URL, price));

        assertDeferred();
    }

    @Test
    void 같은_ID의_후보가_서로_다른_가격을_주면_임의로_선택하지_않는다() {
        respond(FIRST, product(MATCHING_URL, 2_000_000L), product(MATCHING_URL, 2_100_000L));

        assertDeferred();
    }

    @Test
    void 같은_ID에_유효한_가격과_누락된_가격이_섞여도_보류한다() {
        respond(FIRST, product(MATCHING_URL, 2_000_000L), product(MATCHING_URL, null));

        assertDeferred();
    }

    @Test
    void 중복_후보의_가격이_같으면_한_번만_저장한다() {
        respond(FIRST, product(MATCHING_URL, 2_100_000L), product(MATCHING_URL, 2_100_000L));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.UPDATED);

        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(1, 0, 0, 0));
        verify(offers).refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT);
    }

    @Test
    void 다른_판매옵션의_가격은_일치하는_옵션의_가격_선택에_영향을_주지_않는다() {
        respond(FIRST,
            product("https://www.coupang.com/vp/products/999?vendorItemId=57", 1L),
            product(MATCHING_URL, 2_100_000L));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.UNCHANGED);

        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(0, 1, 0, 0));
        verify(offers).refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT);
    }

    @Test
    void 조회_중_DB의_상품_연결이_변경되면_저장_보류로_집계한다() {
        respond(FIRST, product(MATCHING_URL, 2_100_000L));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.SKIPPED);

        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(0, 0, 1, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429})
    void 인증_실패나_호출_제한이면_완료한_상품은_유지하고_남은_호출을_중단한다(int status) {
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(FIRST, SECOND, THIRD));
        respond(FIRST, product(MATCHING_URL, 2_100_000L));
        server.expect(requestTo(searchUrl(SECOND))).andRespond(withStatus(HttpStatusCode.valueOf(status)));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.UPDATED);

        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(1, 0, 1, 1));
        verify(offers).refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT);
        verify(offers, never()).refreshCoupangPrice(eq(SECOND), anyLong(), any());
        verify(offers, never()).refreshCoupangPrice(eq(THIRD), anyLong(), any());
        assertThat(pauses).containsExactly(2100L);
    }

    @Test
    void 개별_조회_오류는_즉시_재시도하지_않고_간격을_둔_뒤_다음_상품을_조회한다() {
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(FIRST, SECOND));
        server.expect(requestTo(searchUrl(FIRST))).andRespond(withStatus(HttpStatusCode.valueOf(500)));
        respond(SECOND, product(MATCHING_URL, 2_100_000L));
        when(offers.refreshCoupangPrice(SECOND, 2_100_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.UPDATED);

        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(1, 0, 1, 0));
        verify(offers, never()).refreshCoupangPrice(eq(FIRST), anyLong(), any());
        verify(offers).refreshCoupangPrice(SECOND, 2_100_000L, CHECKED_AT);
        assertThat(pauses).containsExactly(2100L);
    }

    @Test
    void DB_저장이_실패한_상품은_보류하고_다음_상품은_계속_처리한다() {
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(FIRST, SECOND));
        respond(FIRST, product(MATCHING_URL, 2_100_000L));
        respond(SECOND, product(MATCHING_URL, 2_100_000L));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT))
            .thenThrow(new DataAccessResourceFailureException("test database failure"));
        when(offers.refreshCoupangPrice(SECOND, 2_100_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.UNCHANGED);

        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(0, 1, 1, 0));
        verify(offers).refreshCoupangPrice(SECOND, 2_100_000L, CHECKED_AT);
    }

    @Test
    void 호출_간격_대기_중_중단되면_인터럽트를_복원하고_남은_상품을_호출하지_않는다() {
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(FIRST, SECOND));
        respond(FIRST, product(MATCHING_URL, 2_100_000L));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.UPDATED);
        job = new CoupangPriceRefreshJob(client, offers, clock, millis -> {
            throw new InterruptedException("test shutdown");
        });

        try {
            assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(1, 0, 0, 1));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(offers, never()).refreshCoupangPrice(eq(SECOND), anyLong(), any());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void 갱신_대상이_없으면_API를_호출하지_않는다() {
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of());

        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(0, 0, 0, 0));
        verify(offers, never()).refreshCoupangPrice(any(), anyLong(), any());
        assertThat(pauses).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "0, 20, 0, 0, SUCCESS, 0",
        "3, 17, 0, 0, SUCCESS, 0",
        "3, 2, 15, 0, PARTIAL, 2",
        "1, 0, 1, 18, PARTIAL, 2",
        "0, 0, 20, 0, FAILED, 2",
        "0, 0, 0, 20, FAILED, 2",
        "0, 0, 0, 0, NO_TARGETS, 2"
    })
    void 가격_변경_건수가_아닌_확인_성공_여부로_전체_결과와_종료_코드를_판정한다(
        int updated, int unchanged, int deferred, int notAttempted, String outcome, int exitCode
    ) {
        var summary = new CoupangPriceRefreshJob.Summary(updated, unchanged, deferred, notAttempted);

        assertThat(summary.outcome()).isEqualTo(outcome);
        assertThat(summary.exitCode()).isEqualTo(exitCode);
    }

    @Test
    void 조회_대상부터_읽지_못해도_시작과_실패_로그를_같은_실행_ID로_남긴다() {
        Logger logger = (Logger) LoggerFactory.getLogger(CoupangPriceRefreshJob.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        when(offers.findCoupangPriceRefreshTargets()).thenThrow(new DataAccessResourceFailureException("DB unavailable"));
        try {
            assertThatThrownBy(() -> job.refreshPrices()).isInstanceOf(DataAccessResourceFailureException.class);

            assertThat(appender.list).hasSize(2);
            String runId = appender.list.getFirst().getMDCPropertyMap().get("price_refresh.run_id");
            assertThat(runId).isNotBlank();
            assertThat(appender.list.getLast().getMDCPropertyMap()).containsEntry("price_refresh.run_id", runId);
            assertThat(appender.list.getLast().getKeyValuePairs())
                .anySatisfy(pair -> {
                    assertThat(pair.key).isEqualTo("price_refresh.outcome");
                    assertThat(pair.value).isEqualTo("FAILED");
                });
            assertThat(MDC.get("price_refresh.run_id")).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private void assertDeferred() {
        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(0, 0, 1, 0));
        verify(offers, never()).refreshCoupangPrice(any(), anyLong(), any());
    }

    private void respond(PriceRefreshTarget target, String... products) {
        String response = "{\"rCode\":\"0\",\"data\":{\"productData\":[" + String.join(",", products) + "]}}";
        server.expect(requestTo(searchUrl(target))).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private String searchUrl(PriceRefreshTarget target) {
        return SEARCH_URL + "?keyword=" + URLEncoder.encode(target.productCode(), StandardCharsets.UTF_8) + "&limit=10";
    }

    private String product(String url, Long price) {
        String urlValue = url == null ? "null" : "\"" + url + "\"";
        return """
            {"productId":56,"productName":"노트북","productPrice":%s,"productUrl":%s}
            """.formatted(price, urlValue);
    }
}
