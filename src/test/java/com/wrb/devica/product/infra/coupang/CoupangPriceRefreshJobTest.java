package com.wrb.devica.product.infra.coupang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.wrb.devica.product.dto.PriceRefreshTarget;
import com.wrb.devica.product.domain.OfferStatus;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.client.RestClient;

class CoupangPriceRefreshJobTest {

    private static final String SEARCH_URL = "https://api-gateway.coupang.com"
        + "/v2/providers/affiliate_open_api/apis/openapi/v1/products/search";
    private static final String MATCHING_URL =
        "https://link.coupang.com/re/AFFSDP?pageKey=999&itemId=777&vendorItemId=56";
    private static final LocalDate CHECKED_AT = LocalDate.of(2026, 10, 27);
    private static final PriceRefreshTarget FIRST = target(1L, 10L, "MDH74KH/A");
    private static final PriceRefreshTarget SECOND = target(2L, 20L, "SECOND");
    private static final PriceRefreshTarget THIRD = target(3L, 30L, "THIRD");
    private static final PriceRefreshTarget HIDDEN = new PriceRefreshTarget(4L, 40L, "HIDDEN", "56",
        "https://www.coupang.com/vp/products/999?vendorItemId=56", 1_000_000L, OfferStatus.HIDDEN);

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
        job = new CoupangPriceRefreshJob(client, offers, clock, "https://devica.example", pauses::add);
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

        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(1, 0, 0, 0, 0, 0, 0));
        verify(offers).refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT);
        assertThat(pauses).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "not-a-url",
        "https://example.com/vp/products/56?vendorItemId=56",
        "https://www.coupang.com/vp/products/56?itemId=56",
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

        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(1, 0, 0, 0, 0, 0, 0));
        verify(offers).refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT);
    }

    @Test
    void 다른_판매옵션의_가격은_일치하는_옵션의_가격_선택에_영향을_주지_않는다() {
        respond(FIRST,
            product("https://www.coupang.com/vp/products/999?vendorItemId=57", 1L),
            product(MATCHING_URL, 1_000_000L));
        when(offers.refreshCoupangPrice(FIRST, 1_000_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.UNCHANGED);

        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(0, 1, 0, 0, 0, 0, 0));
        verify(offers).refreshCoupangPrice(FIRST, 1_000_000L, CHECKED_AT);
    }

    @Test
    void 조회_중_DB의_상품_연결이_변경되면_저장_보류로_집계한다() {
        respond(FIRST, product(MATCHING_URL, 2_100_000L));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.SKIPPED);

        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(0, 0, 1, 0, 0, 0, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429})
    void 인증_실패나_호출_제한이면_완료한_상품은_유지하고_남은_호출을_중단한다(int status) {
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(FIRST, SECOND, THIRD));
        respond(FIRST, product(MATCHING_URL, 2_100_000L));
        server.expect(requestTo(searchUrl(SECOND))).andRespond(withStatus(HttpStatusCode.valueOf(status)));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.UPDATED);

        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(1, 0, 1, 1, 0, 0, 0));
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

        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(1, 0, 1, 0, 0, 0, 0));
        verify(offers, never()).refreshCoupangPrice(eq(FIRST), anyLong(), any());
        verify(offers).refreshCoupangPrice(SECOND, 2_100_000L, CHECKED_AT);
        assertThat(pauses).containsExactly(2100L);
    }

    @Test
    void DB_저장이_실패한_상품은_보류하고_다음_상품은_계속_처리한다() {
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(FIRST, SECOND));
        respond(FIRST, product(MATCHING_URL, 2_100_000L));
        respond(SECOND, product(MATCHING_URL, 1_000_000L));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT))
            .thenThrow(new DataAccessResourceFailureException("test database failure"));
        when(offers.refreshCoupangPrice(SECOND, 1_000_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.UNCHANGED);

        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(0, 1, 1, 0, 0, 0, 0));
        verify(offers).refreshCoupangPrice(SECOND, 1_000_000L, CHECKED_AT);
    }

    @Test
    void 첫_상품의_트랜잭션을_시작하지_못하면_현재_실패와_남은_미시도_및_종료_집계를_남긴다() {
        // given
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(FIRST, SECOND, THIRD));
        respond(FIRST, product(MATCHING_URL, 2_100_000L));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT))
            .thenThrow(new CannotCreateTransactionException("test transaction start failure"));

        // when & then
        List<ILoggingEvent> events = capture(() -> assertThat(job.refreshPrices())
            .isEqualTo(new CoupangPriceRefreshJob.Summary(0, 0, 1, 2, 0, 0, 0)));

        assertThat(events).hasSize(5);
        assertField(events.get(1), "offer.id", FIRST.offerId());
        assertField(events.get(1), "price_refresh.result", "DB_FAILURE");
        assertField(events.get(1), "price_refresh.action", "NONE");
        assertField(events.get(1), "error.type", CannotCreateTransactionException.class.getName());
        assertField(events.get(1), "offer.url", FIRST.purchaseUrl());
        assertThat(events.get(1).getKeyValuePairs())
            .noneSatisfy(pair -> assertThat(pair.key).isEqualTo("price_refresh.price_changed"));
        assertField(events.get(2), "offer.id", SECOND.offerId());
        assertField(events.get(2), "price_refresh.result", "NOT_ATTEMPTED");
        assertField(events.get(3), "offer.id", THIRD.offerId());
        assertField(events.get(3), "price_refresh.result", "NOT_ATTEMPTED");
        assertField(events.getLast(), "price_refresh.success", 0);
        assertField(events.getLast(), "price_refresh.total", 3);
        assertField(events.getLast(), "price_refresh.deferred", 1);
        assertField(events.getLast(), "price_refresh.not_attempted", 2);
        assertField(events.getLast(), "price_refresh.outcome", "FAILED");
        String runId = events.getFirst().getMDCPropertyMap().get("price_refresh.run_id");
        assertThat(runId).isNotBlank();
        assertThat(events).allSatisfy(event -> assertThat(event.getMDCPropertyMap())
            .containsEntry("price_refresh.run_id", runId).containsEntry("price_refresh.seller", "COUPANG"));
        verify(offers).findCoupangPriceRefreshTargets();
        verify(offers).refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT);
        verifyNoMoreInteractions(offers);
        assertThat(pauses).isEmpty();
    }

    @Test
    void 일부_성공_후_트랜잭션_시작이_실패해도_성공_집계를_유지하고_남은_호출을_중단한다() {
        // given
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(FIRST, SECOND, THIRD));
        respond(FIRST, product(MATCHING_URL, 2_100_000L));
        respond(SECOND, product(MATCHING_URL, SECOND.price()));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.UPDATED);
        when(offers.refreshCoupangPrice(SECOND, SECOND.price(), CHECKED_AT))
            .thenThrow(new CannotCreateTransactionException("test transaction start failure"));

        // when & then
        List<ILoggingEvent> events = capture(() -> assertThat(job.refreshPrices())
            .isEqualTo(new CoupangPriceRefreshJob.Summary(1, 0, 1, 1, 0, 0, 0)));

        assertThat(events).hasSize(5);
        assertField(events.get(1), "price_refresh.result", "UPDATED");
        assertField(events.get(2), "offer.id", SECOND.offerId());
        assertField(events.get(2), "price_refresh.result", "DB_FAILURE");
        assertField(events.get(3), "offer.id", THIRD.offerId());
        assertField(events.get(3), "price_refresh.result", "NOT_ATTEMPTED");
        assertField(events.getLast(), "price_refresh.success", 1);
        assertField(events.getLast(), "price_refresh.total", 3);
        assertField(events.getLast(), "price_refresh.updated", 1);
        assertField(events.getLast(), "price_refresh.unchanged", 0);
        assertField(events.getLast(), "price_refresh.deferred", 1);
        assertField(events.getLast(), "price_refresh.not_attempted", 1);
        assertField(events.getLast(), "price_refresh.outcome", "PARTIAL");
        verify(offers).findCoupangPriceRefreshTargets();
        verify(offers).refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT);
        verify(offers).refreshCoupangPrice(SECOND, SECOND.price(), CHECKED_AT);
        verifyNoMoreInteractions(offers);
        assertThat(pauses).containsExactly(2100L);
    }

    @Test
    void 호출_간격_대기_중_중단되면_인터럽트를_복원하고_남은_상품을_호출하지_않는다() {
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(FIRST, SECOND));
        respond(FIRST, product(MATCHING_URL, 2_100_000L));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.UPDATED);
        job = new CoupangPriceRefreshJob(client, offers, clock, "https://devica.example", millis -> {
            throw new InterruptedException("test shutdown");
        });

        try {
            assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(1, 0, 0, 1, 0, 0, 0));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(offers, never()).refreshCoupangPrice(eq(SECOND), anyLong(), any());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void 대기_직후_인터럽트가_설정되어도_다음_API를_호출하지_않는다() {
        // given
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(FIRST, SECOND));
        respond(FIRST, product(MATCHING_URL, 2_100_000L));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.UPDATED);
        job = new CoupangPriceRefreshJob(client, offers, clock, "https://devica.example",
            millis -> Thread.currentThread().interrupt());

        try {
            // when
            var summary = job.refreshPrices();

            // then
            assertThat(summary).isEqualTo(new CoupangPriceRefreshJob.Summary(1, 0, 0, 1, 0, 0, 0));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(offers, never()).refreshCoupangPrice(eq(SECOND), anyLong(), any());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void 갱신_대상이_없으면_API를_호출하지_않는다() {
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of());

        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(0, 0, 0, 0, 0, 0, 0));
        verify(offers, never()).refreshCoupangPrice(any(), anyLong(), any());
        assertThat(pauses).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "0, 20, 0, 0, 0, SUCCESS, 0",
        "3, 17, 0, 0, 0, SUCCESS, 0",
        "3, 2, 15, 0, 0, PARTIAL, 2",
        "8, 2, 10, 0, 0, PARTIAL, 2",
        "1, 0, 1, 18, 0, PARTIAL, 2",
        "0, 0, 20, 0, 0, FAILED, 2",
        "0, 0, 0, 20, 0, FAILED, 2",
        "0, 0, 0, 0, 0, NO_TARGETS, 2",
        "0, 0, 0, 0, 2, SUCCESS, 0",
        "1, 1, 0, 0, 2, SUCCESS, 0",
        "0, 0, 1, 0, 2, PARTIAL, 2",
        "0, 0, 0, 1, 2, PARTIAL, 2",
        "1, 0, 1, 1, 2, PARTIAL, 2"
    })
    void 확인_성공과_숨김_유지를_구분해_전체_결과와_종료_코드를_판정한다(
        int updated, int unchanged, int deferred, int notAttempted, int hiddenUnchanged, String outcome, int exitCode
    ) {
        var summary = new CoupangPriceRefreshJob.Summary(updated, unchanged, deferred, notAttempted, 0, 0,
            hiddenUnchanged);

        assertThat(summary.outcome()).isEqualTo(outcome);
        assertThat(summary.exitCode()).isEqualTo(exitCode);
        assertThat(summary.success()).isEqualTo(updated + unchanged);
        assertThat(summary.total()).isEqualTo(updated + unchanged + hiddenUnchanged + deferred + notAttempted);
    }

    @Test
    void 판매처별_성공과_대상_수를_남기고_보류와_복구를_중복_집계하지_않는다() {
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(FIRST, SECOND, THIRD));
        respond(FIRST, product(MATCHING_URL, 2_100_000L));
        respond(SECOND, product(MATCHING_URL, SECOND.price()));
        respond(THIRD, product("https://www.coupang.com/vp/products/999?vendorItemId=57", 1L));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.RESTORED);
        when(offers.refreshCoupangPrice(SECOND, SECOND.price(), CHECKED_AT)).thenReturn(PriceRefreshResult.UNCHANGED);

        List<ILoggingEvent> events = capture(() -> {
            var summary = job.refreshPrices();
            assertThat(summary.success()).isEqualTo(2);
            assertThat(summary.total()).isEqualTo(3);
            assertThat(summary.hidden()).isZero();
            assertThat(summary.restored()).isEqualTo(1);
        });

        String runId = events.getFirst().getMDCPropertyMap().get("price_refresh.run_id");
        assertThat(runId).isNotBlank();
        assertThat(events).hasSize(5).allSatisfy(event -> assertThat(event.getMDCPropertyMap())
            .containsEntry("price_refresh.seller", "COUPANG")
            .containsEntry("price_refresh.run_id", runId));
        assertField(events.getLast(), "price_refresh.success", 2);
        assertField(events.getLast(), "price_refresh.total", 3);
        assertField(events.getLast(), "price_refresh.outcome", "PARTIAL");
        assertThat(MDC.get("price_refresh.seller")).isNull();
        assertThat(MDC.get("price_refresh.run_id")).isNull();
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
            assertThat(appender.list).allSatisfy(event -> assertThat(event.getMDCPropertyMap())
                .containsEntry("price_refresh.seller", "COUPANG"));
            assertThat(appender.list.getLast().getKeyValuePairs()).noneSatisfy(pair ->
                assertThat(pair.key).isIn("price_refresh.success", "price_refresh.total"));
            assertThat(appender.list.getLast().getKeyValuePairs())
                .anySatisfy(pair -> {
                    assertThat(pair.key).isEqualTo("price_refresh.outcome");
                    assertThat(pair.value).isEqualTo("FAILED");
                });
            assertThat(MDC.get("price_refresh.run_id")).isNull();
            assertThat(MDC.get("price_refresh.seller")).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void 모든_후보의_ID가_달라도_저장하지_않고_비교_근거와_확인_링크를_남긴다() {
        respond(FIRST, product("https://www.coupang.com/vp/products/999?vendorItemId=57", 1L));

        List<ILoggingEvent> events = capture(() -> assertThat(job.refreshPrices())
            .isEqualTo(new CoupangPriceRefreshJob.Summary(0, 0, 1, 0, 0, 0, 0)));

        verify(offers, never()).hideCoupangOffer(any(), any());
        verify(offers, never()).refreshCoupangPrice(any(), anyLong(), any());
        ILoggingEvent event = resultEvent(events);
        assertField(event, "price_refresh.result", "NO_MATCH");
        assertField(event, "price_refresh.action", "NONE");
        assertField(event, "offer.vendor_item_id", "56");
        assertField(event, "search.vendor_item_ids", List.of("57"));
        assertField(event, "search.candidate_count", 1);
        assertField(event, "offer.url", FIRST.purchaseUrl());
        assertField(event, "product.url", "https://devica.example/products/10");
    }

    @Test
    void 검색_상위_10개에_기준_ID가_없어도_어떤_DB_변경도_요청하지_않는다() {
        // given
        String[] candidates = IntStream.rangeClosed(57, 66)
            .mapToObj(id -> product("https://www.coupang.com/vp/products/999?vendorItemId=" + id, 1L))
            .toArray(String[]::new);
        respond(FIRST, candidates);

        // when
        List<ILoggingEvent> events = capture(this::assertDeferred);

        // then
        verify(offers).findCoupangPriceRefreshTargets();
        verifyNoMoreInteractions(offers);
        assertField(resultEvent(events), "price_refresh.result", "NO_MATCH");
        assertField(resultEvent(events), "price_refresh.action", "NONE");
        assertField(resultEvent(events), "search.candidate_count", 10);
        assertField(events.getLast(), "price_refresh.hidden", 0);
    }

    @Test
    void 보류한_상품도_다음_실행에서_같은_ID를_찾으면_가격을_갱신한다() {
        // given
        respond(FIRST, product("https://www.coupang.com/vp/products/999?vendorItemId=57", 1L));
        respond(FIRST, product(MATCHING_URL, 2_100_000L));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.UPDATED);

        // when
        var deferred = job.refreshPrices();
        var refreshed = job.refreshPrices();

        // then
        assertThat(deferred).isEqualTo(new CoupangPriceRefreshJob.Summary(0, 0, 1, 0, 0, 0, 0));
        assertThat(refreshed).isEqualTo(new CoupangPriceRefreshJob.Summary(1, 0, 0, 0, 0, 0, 0));
        verify(offers).refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT);
        verify(offers, never()).hideCoupangOffer(any(), any());
    }

    @Test
    void 이미_숨긴_상품의_ID가_명확히_불일치하면_DB_변경_없이_숨김_유지로_기록한다() {
        // given
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(HIDDEN));
        respond(HIDDEN, product("https://www.coupang.com/vp/products/999?vendorItemId=57", 1L));

        // when & then
        List<ILoggingEvent> events = capture(() -> {
            var summary = job.refreshPrices();
            assertThat(summary).isEqualTo(new CoupangPriceRefreshJob.Summary(0, 0, 0, 0, 0, 0, 1));
            assertThat(summary.success()).isZero();
            assertThat(summary.total()).isEqualTo(1);
            assertThat(summary.outcome()).isEqualTo("SUCCESS");
            assertThat(summary.exitCode()).isZero();
        });

        verify(offers).findCoupangPriceRefreshTargets();
        verifyNoMoreInteractions(offers);
        assertField(resultEvent(events), "price_refresh.result", "HIDDEN_UNCHANGED");
        assertField(resultEvent(events), "price_refresh.action", "NONE");
        assertField(resultEvent(events), "offer.id", HIDDEN.offerId());
        assertField(resultEvent(events), "search.vendor_item_ids", List.of("57"));
        assertField(events.getLast(), "price_refresh.hidden_unchanged", 1);
        assertField(events.getLast(), "price_refresh.success", 0);
        assertField(events.getLast(), "price_refresh.deferred", 0);
        assertField(events.getLast(), "price_refresh.total", 1);
        assertField(events.getLast(), "price_refresh.outcome", "SUCCESS");
    }

    @ParameterizedTest
    @MethodSource("hiddenDeferrals")
    void 숨긴_상품도_응답을_신뢰할_수_없으면_숨김_유지가_아닌_보류로_기록한다(
        String reason, String[] products
    ) {
        // given
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(HIDDEN));
        respond(HIDDEN, products);

        // when & then
        List<ILoggingEvent> events = capture(this::assertDeferred);

        verify(offers, never()).hideCoupangOffer(any(), any());
        assertField(resultEvent(events), "price_refresh.result", reason);
        assertField(resultEvent(events), "price_refresh.action", "NONE");
        assertField(events.getLast(), "price_refresh.hidden_unchanged", 0);
        assertField(events.getLast(), "price_refresh.deferred", 1);
        assertField(events.getLast(), "price_refresh.outcome", "FAILED");
    }

    private static Stream<Arguments> hiddenDeferrals() {
        return Stream.of(
            Arguments.of("NO_SEARCH_RESULTS", new String[0]),
            Arguments.of("IDENTIFIER_UNAVAILABLE", new String[]{product(null, 1L)}),
            Arguments.of("IDENTIFIER_UNAVAILABLE", new String[]{product("https://example.com/product", 1L)}),
            Arguments.of("IDENTIFIER_UNAVAILABLE",
                new String[]{product("https://www.coupang.com/vp/products/999?itemId=777", 1L)}),
            Arguments.of("INVALID_PRICE", new String[]{product(MATCHING_URL, null)}),
            Arguments.of("INVALID_PRICE", new String[]{product(MATCHING_URL, 0L)}),
            Arguments.of("CONFLICTING_PRICES",
                new String[]{product(MATCHING_URL, 1_000_000L), product(MATCHING_URL, 2_000_000L)})
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500})
    void 숨긴_상품의_API_실패도_보류로_기록한다(int status) {
        // given
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(HIDDEN));
        server.expect(requestTo(searchUrl(HIDDEN))).andRespond(withStatus(HttpStatusCode.valueOf(status)));

        // when & then
        List<ILoggingEvent> events = capture(this::assertDeferred);

        assertField(resultEvent(events), "price_refresh.result", "API_FAILURE");
        assertField(events.getLast(), "price_refresh.hidden_unchanged", 0);
        assertField(events.getLast(), "price_refresh.deferred", 1);
        assertField(events.getLast(), "price_refresh.outcome", "FAILED");
        verify(offers, never()).hideCoupangOffer(any(), any());
    }

    @Test
    void 숨긴_상품의_복구_저장이_실패해도_숨김_유지가_아닌_보류로_기록한다() {
        // given
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(HIDDEN));
        respond(HIDDEN, product(MATCHING_URL, 2_100_000L));
        when(offers.refreshCoupangPrice(HIDDEN, 2_100_000L, CHECKED_AT))
            .thenThrow(new DataAccessResourceFailureException("test database failure"));

        // when & then
        List<ILoggingEvent> events = capture(() -> assertThat(job.refreshPrices())
            .isEqualTo(new CoupangPriceRefreshJob.Summary(0, 0, 1, 0, 0, 0, 0)));

        assertField(resultEvent(events), "price_refresh.result", "DB_FAILURE");
        assertField(events.getLast(), "price_refresh.hidden_unchanged", 0);
        assertField(events.getLast(), "price_refresh.deferred", 1);
        assertField(events.getLast(), "price_refresh.outcome", "FAILED");
        verify(offers, never()).hideCoupangOffer(any(), any());
    }

    @ParameterizedTest
    @ValueSource(longs = {1_000_000L, 2_100_000L})
    void 숨김_유지_후_다음_실행에서_같은_ID를_찾으면_가격_변경_여부와_무관하게_복구한다(long price) {
        // given
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(HIDDEN));
        respond(HIDDEN, product("https://www.coupang.com/vp/products/999?vendorItemId=57", 1L));
        respond(HIDDEN, product(MATCHING_URL, price));
        when(offers.refreshCoupangPrice(HIDDEN, price, CHECKED_AT)).thenReturn(PriceRefreshResult.RESTORED);

        // when
        var maintained = job.refreshPrices();
        List<ILoggingEvent> events = capture(() -> {
            var restored = job.refreshPrices();
            // then
            assertThat(restored.hiddenUnchanged()).isZero();
            assertThat(restored.deferred()).isZero();
            assertThat(restored.restored()).isEqualTo(1);
            assertThat(restored.success()).isEqualTo(1);
            assertThat(restored.updated()).isEqualTo(price == HIDDEN.price() ? 0 : 1);
            assertThat(restored.unchanged()).isEqualTo(price == HIDDEN.price() ? 1 : 0);
        });

        assertThat(maintained).isEqualTo(new CoupangPriceRefreshJob.Summary(0, 0, 0, 0, 0, 0, 1));
        assertField(resultEvent(events), "price_refresh.result", "RESTORED");
        assertField(events.getLast(), "price_refresh.hidden_unchanged", 0);
        verify(offers).refreshCoupangPrice(HIDDEN, price, CHECKED_AT);
        verify(offers, never()).hideCoupangOffer(any(), any());
    }

    @Test
    void 숨김_유지와_가격_확인이_함께_있어도_성공_건수는_가격_확인만_센다() {
        // given
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(HIDDEN, FIRST));
        respond(HIDDEN, product("https://www.coupang.com/vp/products/999?vendorItemId=57", 1L));
        respond(FIRST, product(MATCHING_URL, FIRST.price()));
        when(offers.refreshCoupangPrice(FIRST, FIRST.price(), CHECKED_AT)).thenReturn(PriceRefreshResult.UNCHANGED);

        // when & then
        List<ILoggingEvent> events = capture(() -> assertThat(job.refreshPrices())
            .isEqualTo(new CoupangPriceRefreshJob.Summary(0, 1, 0, 0, 0, 0, 1)));

        assertField(events.getLast(), "price_refresh.hidden_unchanged", 1);
        assertField(events.getLast(), "price_refresh.success", 1);
        assertField(events.getLast(), "price_refresh.total", 2);
        assertField(events.getLast(), "price_refresh.outcome", "SUCCESS");
        verify(offers, never()).refreshCoupangPrice(eq(HIDDEN), anyLong(), any());
        verify(offers, never()).hideCoupangOffer(any(), any());
    }

    @Test
    void 숨김_유지_후_API가_중단되면_유지와_보류와_미시도를_별도로_집계한다() {
        // given
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of(HIDDEN, FIRST, SECOND));
        respond(HIDDEN, product("https://www.coupang.com/vp/products/999?vendorItemId=57", 1L));
        server.expect(requestTo(searchUrl(FIRST))).andRespond(withStatus(HttpStatusCode.valueOf(429)));

        // when & then
        List<ILoggingEvent> events = capture(() -> {
            var summary = job.refreshPrices();
            assertThat(summary).isEqualTo(new CoupangPriceRefreshJob.Summary(0, 0, 1, 1, 0, 0, 1));
            assertThat(summary.exitCode()).isEqualTo(2);
        });

        assertField(events.get(1), "price_refresh.result", "HIDDEN_UNCHANGED");
        assertField(events.get(2), "price_refresh.result", "API_FAILURE");
        assertField(events.get(3), "price_refresh.result", "NOT_ATTEMPTED");
        assertField(events.getLast(), "price_refresh.hidden_unchanged", 1);
        assertField(events.getLast(), "price_refresh.success", 0);
        assertField(events.getLast(), "price_refresh.deferred", 1);
        assertField(events.getLast(), "price_refresh.not_attempted", 1);
        assertField(events.getLast(), "price_refresh.total", 3);
        assertField(events.getLast(), "price_refresh.outcome", "PARTIAL");
        verify(offers).findCoupangPriceRefreshTargets();
        verifyNoMoreInteractions(offers);
        assertThat(pauses).containsExactly(2100L);
    }

    @Test
    void 검색_결과가_비면_NO_MATCH와_구분하고_숨기지_않는다() {
        respond(FIRST);

        List<ILoggingEvent> events = capture(this::assertDeferred);

        assertField(resultEvent(events), "price_refresh.result", "NO_SEARCH_RESULTS");
        verify(offers, never()).hideCoupangOffer(any(), any());
    }

    @Test
    void 다른_ID와_ID_누락과_잘못된_URL이_섞이면_불일치로_확정하지_않는다() {
        respond(FIRST,
            product("https://www.coupang.com/vp/products/999?vendorItemId=57", 1L),
            product("https://www.coupang.com/vp/products/999?itemId=777", 1L), product(null, 1L));

        List<ILoggingEvent> events = capture(this::assertDeferred);

        ILoggingEvent event = resultEvent(events);
        assertField(event, "price_refresh.result", "IDENTIFIER_UNAVAILABLE");
        assertField(event, "search.invalid_url_count", 1);
        assertField(event, "search.missing_id_count", 1);
        verify(offers, never()).hideCoupangOffer(any(), any());
    }

    @Test
    void 일치한_상품이_있으면_다른_후보의_URL_문제는_갱신을_막지_않는다() {
        respond(FIRST, product(null, 1L), product(MATCHING_URL, 2_100_000L));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT)).thenReturn(PriceRefreshResult.UPDATED);

        List<ILoggingEvent> events = capture(() -> assertThat(job.refreshPrices())
            .isEqualTo(new CoupangPriceRefreshJob.Summary(1, 0, 0, 0, 0, 0, 0)));

        ILoggingEvent event = resultEvent(events);
        assertField(event, "price_refresh.price_changed", true);
        assertField(event, "price_refresh.previous_price", 1_000_000L);
        assertField(event, "price_refresh.current_price", 2_100_000L);
        verify(offers, never()).hideCoupangOffer(any(), any());
    }

    @ParameterizedTest
    @ValueSource(longs = {1_000_000L, 2_100_000L})
    void 노출_복구와_가격_변경을_각각_집계하고_알림_필드에_남긴다(long price) {
        respond(FIRST, product(MATCHING_URL, price));
        when(offers.refreshCoupangPrice(FIRST, price, CHECKED_AT)).thenReturn(PriceRefreshResult.RESTORED);

        List<ILoggingEvent> events = capture(() -> {
            var summary = job.refreshPrices();
            assertThat(summary.restored()).isEqualTo(1);
            assertThat(summary.updated()).isEqualTo(price == FIRST.price() ? 0 : 1);
            assertThat(summary.unchanged()).isEqualTo(price == FIRST.price() ? 1 : 0);
        });
        assertField(resultEvent(events), "price_refresh.action", "RESTORED");
        assertField(resultEvent(events), "price_refresh.price_changed", price != FIRST.price());
    }

    @Test
    void 가격_저장이_실패하면_변경_알림을_남기지_않고_보류를_한_번만_집계한다() {
        respond(FIRST, product(MATCHING_URL, 2_100_000L));
        when(offers.refreshCoupangPrice(FIRST, 2_100_000L, CHECKED_AT))
            .thenThrow(new DataAccessResourceFailureException("DB unavailable"));

        List<ILoggingEvent> events = capture(() -> assertThat(job.refreshPrices())
            .isEqualTo(new CoupangPriceRefreshJob.Summary(0, 0, 1, 0, 0, 0, 0)));

        assertField(resultEvent(events), "price_refresh.result", "DB_FAILURE");
        assertField(resultEvent(events), "price_refresh.action", "NONE");
        assertThat(resultEvent(events).getKeyValuePairs())
            .noneSatisfy(pair -> assertThat(pair.key).isEqualTo("price_refresh.price_changed"));
    }

    @Test
    void API_오류도_에러_종류와_확인_링크를_같이_남긴다() {
        server.expect(requestTo(searchUrl(FIRST))).andRespond(withStatus(HttpStatusCode.valueOf(429)));

        List<ILoggingEvent> events = capture(this::assertDeferred);

        assertField(resultEvent(events), "price_refresh.result", "API_FAILURE");
        assertField(resultEvent(events), "error.code", "RATE_LIMITED");
        assertField(resultEvent(events), "offer.url", FIRST.purchaseUrl());
        verify(offers, never()).hideCoupangOffer(any(), any());
    }

    private List<ILoggingEvent> capture(Runnable execution) {
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
        try {
            execution.run();
            return List.copyOf(appender.list);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private ILoggingEvent resultEvent(List<ILoggingEvent> events) {
        return events.stream().filter(event -> event.getKeyValuePairs() != null
            && event.getKeyValuePairs().stream().anyMatch(pair -> "price_refresh.result".equals(pair.key)))
            .findFirst().orElseThrow();
    }

    private void assertField(ILoggingEvent event, String key, Object value) {
        assertThat(event.getKeyValuePairs()).anySatisfy(pair -> {
            assertThat(pair.key).isEqualTo(key);
            assertThat(pair.value).isEqualTo(value);
        });
    }

    private static PriceRefreshTarget target(long offerId, long productId, String code) {
        return new PriceRefreshTarget(offerId, productId, code, "56",
            "https://www.coupang.com/vp/products/999?vendorItemId=56", 1_000_000L, OfferStatus.ON_SALE);
    }

    private void assertDeferred() {
        assertThat(job.refreshPrices()).isEqualTo(new CoupangPriceRefreshJob.Summary(0, 0, 1, 0, 0, 0, 0));
        verify(offers, never()).refreshCoupangPrice(any(), anyLong(), any());
    }

    private void respond(PriceRefreshTarget target, String... products) {
        String response = "{\"rCode\":\"0\",\"data\":{\"productData\":[" + String.join(",", products) + "]}}";
        server.expect(requestTo(searchUrl(target))).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private String searchUrl(PriceRefreshTarget target) {
        return SEARCH_URL + "?keyword=" + URLEncoder.encode(target.productCode(), StandardCharsets.UTF_8) + "&limit=10";
    }

    private static String product(String url, Long price) {
        String urlValue = url == null ? "null" : "\"" + url + "\"";
        return """
            {"productId":56,"productName":"노트북","productPrice":%s,"productUrl":%s}
            """.formatted(price, urlValue);
    }
}
