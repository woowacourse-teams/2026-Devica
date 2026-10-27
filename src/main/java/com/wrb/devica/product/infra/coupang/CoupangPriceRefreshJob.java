package com.wrb.devica.product.infra.coupang;

import com.wrb.devica.product.dto.PriceRefreshTarget;
import com.wrb.devica.product.service.ProductOfferService;
import com.wrb.devica.product.service.ProductOfferService.PriceRefreshResult;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
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
    private final String publicBaseUrl;

    public CoupangPriceRefreshJob(CoupangProductSearchClient client, ProductOfferService offers, Clock clock,
                                  String publicBaseUrl) {
        this(client, offers, clock, publicBaseUrl, Thread::sleep);
    }

    CoupangPriceRefreshJob(CoupangProductSearchClient client, ProductOfferService offers, Clock clock,
                           String publicBaseUrl, Pause pause) {
        this.client = client;
        this.offers = offers;
        this.clock = clock;
        this.pause = pause;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Scheduled(cron = "${coupang.price-refresh.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public Summary refreshPrices() {
        long started = System.nanoTime();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("price_refresh.run_id", UUID.randomUUID().toString());
             MDC.MDCCloseable ignoredSeller = MDC.putCloseable("price_refresh.seller", "COUPANG")) {
            log.info("쿠팡 가격 최신화 시작");
            try {
                Summary summary = refreshTargets();
                log.atInfo().addKeyValue("price_refresh.outcome", summary.outcome())
                    .addKeyValue("price_refresh.success", summary.success())
                    .addKeyValue("price_refresh.total", summary.total())
                    .addKeyValue("price_refresh.updated", summary.updated())
                    .addKeyValue("price_refresh.unchanged", summary.unchanged())
                    .addKeyValue("price_refresh.deferred", summary.deferred())
                    .addKeyValue("price_refresh.not_attempted", summary.notAttempted())
                    .addKeyValue("price_refresh.hidden", summary.hidden())
                    .addKeyValue("price_refresh.restored", summary.restored())
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
        int hidden = 0;
        int restored = 0;
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
                SearchInspection inspection = inspect(client.search(target.productCode()), target.vendorItemId());
                if (inspection.matches().isEmpty()) {
                    String reason = inspection.noMatchReason();
                    String action = "NONE";
                    if ("NO_MATCH".equals(reason)) {
                        PriceRefreshResult result = offers.hideCoupangOffer(target, LocalDate.now(clock));
                        action = result.name();
                        if (result == PriceRefreshResult.HIDDEN) {
                            hidden++;
                        } else if (result == PriceRefreshResult.SKIPPED) {
                            reason = "SKIPPED";
                        }
                    }
                    deferred++;
                    resultLog(target, reason, action, started, inspection).log("쿠팡 가격 갱신 보류");
                    continue;
                }
                List<Long> prices = inspection.matches().stream()
                    .map(CoupangProductSearchClient.Product::productPrice).toList();
                if (prices.stream().anyMatch(price -> price == null || price <= 0)) {
                    deferred++;
                    resultLog(target, "INVALID_PRICE", "NONE", started, inspection).log("쿠팡 가격 갱신 보류");
                    continue;
                }
                if (prices.stream().distinct().count() != 1) {
                    deferred++;
                    resultLog(target, "CONFLICTING_PRICES", "NONE", started, inspection).log("쿠팡 가격 갱신 보류");
                    continue;
                }
                long price = prices.getFirst();
                PriceRefreshResult result = offers.refreshCoupangPrice(target, price, LocalDate.now(clock));
                if (result == PriceRefreshResult.SKIPPED) {
                    deferred++;
                    resultLog(target, "SKIPPED", "NONE", started, inspection).log("쿠팡 저장 대상 변경으로 갱신 보류");
                    continue;
                }
                boolean priceChanged = target.price() != price;
                if (priceChanged) {
                    updated++;
                } else {
                    unchanged++;
                }
                if (result == PriceRefreshResult.RESTORED) {
                    restored++;
                }
                // 서비스 트랜잭션이 커밋된 뒤에만 변경 알림의 근거를 남긴다.
                resultLog(target, result.name(), result.name(), started, inspection)
                    .addKeyValue("price_refresh.price_changed", priceChanged)
                    .addKeyValue("price_refresh.previous_price", target.price())
                    .addKeyValue("price_refresh.current_price", price)
                    .log("쿠팡 판매상품 가격 확인");
            } catch (CoupangApiException exception) {
                deferred++;
                targetLog(log.atWarn(), target, "API_FAILURE", "NONE", started)
                    .addKeyValue("error.code", exception.getReason().name())
                    .log("쿠팡 가격 조회 실패로 갱신 보류");
                if (exception.getReason() == CoupangApiException.Reason.AUTHENTICATION_FAILED
                    || exception.getReason() == CoupangApiException.Reason.RATE_LIMITED) {
                    break;
                }
            } catch (DataAccessException exception) {
                deferred++;
                targetLog(log.atError(), target, "DB_FAILURE", "NONE", started)
                    .addKeyValue("error.type", exception.getClass().getName())
                    .log("쿠팡 가격 저장 실패로 해당 상품 갱신 취소");
            }
        }
        for (PriceRefreshTarget target : targets.subList(attempted, targets.size())) {
            targetLog(log.atInfo(), target, "NOT_ATTEMPTED", "NONE", System.nanoTime())
                .log("쿠팡 가격 최신화 중단으로 미시도");
        }
        return new Summary(updated, unchanged, deferred, targets.size() - attempted, hidden, restored);
    }

    private SearchInspection inspect(List<CoupangProductSearchClient.Product> candidates, String expectedId) {
        List<CoupangProductSearchClient.Product> matches = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        int invalidUrls = 0;
        int missingIds = 0;
        for (CoupangProductSearchClient.Product candidate : candidates) {
            CoupangProductUrl identifiers = CoupangProductUrl.parse(candidate.productUrl()).orElse(null);
            if (identifiers == null) {
                invalidUrls++;
            } else if (identifiers.vendorItemId() == null) {
                missingIds++;
            } else {
                ids.add(identifiers.vendorItemId());
                if (identifiers.vendorItemId().equals(expectedId)) {
                    matches.add(candidate);
                }
            }
        }
        return new SearchInspection(candidates.size(), invalidUrls, missingIds, ids, matches);
    }

    private record SearchInspection(int candidateCount, int invalidUrls, int missingIds, List<String> vendorItemIds,
                                    List<CoupangProductSearchClient.Product> matches) {

        String noMatchReason() {
            if (candidateCount == 0) {
                return "NO_SEARCH_RESULTS";
            }
            return invalidUrls + missingIds > 0 ? "IDENTIFIER_UNAVAILABLE" : "NO_MATCH";
        }
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

    private LoggingEventBuilder resultLog(PriceRefreshTarget target, String result, String action, long started,
                                           SearchInspection inspection) {
        return targetLog(log.atInfo(), target, result, action, started)
            .addKeyValue("search.candidate_count", inspection.candidateCount())
            .addKeyValue("search.invalid_url_count", inspection.invalidUrls())
            .addKeyValue("search.missing_id_count", inspection.missingIds())
            .addKeyValue("search.vendor_item_ids", inspection.vendorItemIds());
    }

    private LoggingEventBuilder targetLog(LoggingEventBuilder event, PriceRefreshTarget target,
                                           String result, String action, long started) {
        return event.addKeyValue("offer.id", target.offerId())
            .addKeyValue("offer.url", target.purchaseUrl())
            .addKeyValue("offer.vendor_item_id", target.vendorItemId())
            .addKeyValue("product.id", target.productId())
            .addKeyValue("product.code", target.productCode())
            .addKeyValue("product.url", publicBaseUrl + "/products/" + target.productId())
            .addKeyValue("search.keyword", target.productCode())
            .addKeyValue("server.address", "api-gateway.coupang.com")
            .addKeyValue("event.duration", System.nanoTime() - started)
            .addKeyValue("price_refresh.result", result)
            .addKeyValue("price_refresh.action", action);
    }

    // hidden은 deferred에, restored는 updated + unchanged에 포함된다.
    public record Summary(int updated, int unchanged, int deferred, int notAttempted, int hidden, int restored) {

        public int success() {
            return updated + unchanged;
        }

        public int total() {
            return success() + deferred + notAttempted;
        }

        public String outcome() {
            if (total() == 0) {
                return "NO_TARGETS";
            }
            if (success() == 0) {
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
