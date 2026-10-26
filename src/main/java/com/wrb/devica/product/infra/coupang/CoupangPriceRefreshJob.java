package com.wrb.devica.product.infra.coupang;

import com.wrb.devica.product.dto.PriceRefreshTarget;
import com.wrb.devica.product.service.ProductOfferService;
import com.wrb.devica.product.service.ProductOfferService.PriceRefreshResult;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * API 호출과 호출 간격 대기 중에는 DB 트랜잭션을 열지 않는다.
 */
@Slf4j
public class CoupangPriceRefreshJob {

    private final CoupangProductSearchClient client;
    private final ProductOfferService offers;
    private final Clock clock;
    private final Pause pause;

    public CoupangPriceRefreshJob(CoupangProductSearchClient client, ProductOfferService offers, Clock clock) {
        this(client, offers, clock, Thread::sleep);
    }

    CoupangPriceRefreshJob(CoupangProductSearchClient client, ProductOfferService offers, Clock clock, Pause pause) {
        this.client = client;
        this.offers = offers;
        this.clock = clock;
        this.pause = pause;
    }

    @Scheduled(cron = "${coupang.price-refresh.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public Summary refreshPrices() {
        long started = System.nanoTime();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("price_refresh.run_id", UUID.randomUUID().toString())) {
            log.info("쿠팡 가격 최신화 시작");
            try {
                Summary summary = refreshTargets();
                log.atInfo().addKeyValue("price_refresh.outcome", summary.outcome())
                    .addKeyValue("price_refresh.updated", summary.updated())
                    .addKeyValue("price_refresh.unchanged", summary.unchanged())
                    .addKeyValue("price_refresh.deferred", summary.deferred())
                    .addKeyValue("price_refresh.not_attempted", summary.notAttempted())
                    .addKeyValue("event.duration", System.nanoTime() - started)
                    .log("쿠팡 가격 최신화 종료");
                return summary;
            } catch (RuntimeException exception) {
                log.atError().addKeyValue("price_refresh.outcome", "FAILED")
                    .addKeyValue("event.duration", System.nanoTime() - started).setCause(exception)
                    .log("쿠팡 가격 최신화 실행 실패");
                throw exception;
            }
        }
    }

    private Summary refreshTargets() {
        List<PriceRefreshTarget> targets = offers.findCoupangPriceRefreshTargets();
        int updated = 0;
        int unchanged = 0;
        int deferred = 0;
        int attempted = 0;
        for (PriceRefreshTarget target : targets) {
            if (attempted > 0 && !waitForNextRequest()) {
                break;
            }
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            attempted++;
            long started = System.nanoTime();
            try {
                List<CoupangProductSearchClient.Product> matches = client.search(target.productCode()).stream()
                    .filter(product -> matchesVendorItemId(target.vendorItemId(), product.productUrl()))
                    .toList();
                if (matches.isEmpty()) {
                    deferred++;
                    logResult(target, "NO_MATCH", started);
                    continue;
                }
                List<Long> prices = matches.stream().map(CoupangProductSearchClient.Product::productPrice).toList();
                if (prices.stream().anyMatch(price -> price == null || price <= 0)) {
                    deferred++;
                    logResult(target, "INVALID_PRICE", started);
                    continue;
                }
                if (prices.stream().distinct().count() != 1) {
                    deferred++;
                    logResult(target, "CONFLICTING_PRICES", started);
                    continue;
                }
                PriceRefreshResult result = offers.refreshCoupangPrice(target, prices.getFirst(), LocalDate.now(clock));
                switch (result) {
                    case UPDATED -> updated++;
                    case UNCHANGED -> unchanged++;
                    case SKIPPED -> deferred++;
                }
                logResult(target, result.name(), started);
            } catch (CoupangApiException exception) {
                deferred++;
                log.atWarn().addKeyValue("offer.id", target.offerId())
                    .addKeyValue("product.code", target.productCode())
                    .addKeyValue("server.address", "api-gateway.coupang.com")
                    .addKeyValue("event.duration", System.nanoTime() - started)
                    .addKeyValue("error.type", exception.getClass().getName())
                    .addKeyValue("error.code", exception.reason().name())
                    .log("쿠팡 가격 조회 실패로 갱신 보류");
                if (exception.reason() == CoupangApiException.Reason.AUTHENTICATION_FAILED
                    || exception.reason() == CoupangApiException.Reason.RATE_LIMITED) {
                    break;
                }
            } catch (DataAccessException exception) {
                deferred++;
                log.atError().addKeyValue("offer.id", target.offerId()).setCause(exception)
                    .addKeyValue("product.code", target.productCode())
                    .log("쿠팡 가격 저장 실패로 해당 상품 갱신 취소");
            }
        }
        for (PriceRefreshTarget target : targets.subList(attempted, targets.size())) {
            log.atInfo().addKeyValue("offer.id", target.offerId())
                .addKeyValue("product.code", target.productCode())
                .addKeyValue("price_refresh.result", "NOT_ATTEMPTED")
                .log("쿠팡 가격 최신화 중단으로 미시도");
        }
        return new Summary(updated, unchanged, deferred, targets.size() - attempted);
    }

    private boolean matchesVendorItemId(String expected, String url) {
        return expected != null && !expected.isBlank() && CoupangProductUrl.parse(url)
            .map(identifiers -> expected.equals(identifiers.vendorItemId()))
            .orElse(false);
    }

    private boolean waitForNextRequest() {
        try {
            pause.sleep(2100);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void logResult(PriceRefreshTarget target, String result, long started) {
        log.atInfo().addKeyValue("offer.id", target.offerId())
            .addKeyValue("product.code", target.productCode())
            .addKeyValue("server.address", "api-gateway.coupang.com")
            .addKeyValue("event.duration", System.nanoTime() - started)
            .addKeyValue("price_refresh.result", result)
            .log("쿠팡 판매상품 가격 확인");
    }

    public record Summary(int updated, int unchanged, int deferred, int notAttempted) {

        public String outcome() {
            if (updated + unchanged + deferred + notAttempted == 0) {
                return "NO_TARGETS";
            }
            if (updated + unchanged == 0) {
                return "FAILED";
            }
            return deferred + notAttempted == 0 ? "SUCCESS" : "PARTIAL";
        }

        public int exitCode() {
            return "SUCCESS".equals(outcome()) ? 0 : 2;
        }
    }

    @FunctionalInterface
    interface Pause {
        void sleep(long millis) throws InterruptedException;
    }
}
