package com.wrb.devica.product.infra.coupang;

import com.wrb.devica.product.domain.OfferStatus;
import com.wrb.devica.product.dto.PriceRefreshTarget;
import com.wrb.devica.product.service.ProductOfferService;
import com.wrb.devica.product.service.ProductOfferService.PriceRefreshResult;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.CannotCreateTransactionException;

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
        try (MDC.MDCCloseable ignored = MDC.putCloseable("price_refresh.run_id", UUID.randomUUID().toString());
             MDC.MDCCloseable ignoredSeller = MDC.putCloseable("price_refresh.seller", "COUPANG")) {
            return refreshRun(System.nanoTime());
        }
    }

    private Summary refreshRun(long started) {
        log.info("쿠팡 가격 최신화 시작");
        try {
            Summary summary = refreshTargets();
            logSummary(summary, started);
            return summary;
        } catch (RuntimeException exception) {
            logFailure(exception, started);
            throw exception;
        }
    }

    private Summary refreshTargets() {
        List<PriceRefreshTarget> targets = offers.findCoupangPriceRefreshTargets();
        List<Attempt> attempts = new ArrayList<>();
        while (canContinue(targets.size(), attempts)) {
            attempts.add(refreshTarget(targets.get(attempts.size())));
        }
        targets.subList(attempts.size(), targets.size()).forEach(this::logNotAttempted);
        return summarize(attempts, targets.size());
    }

    private boolean canContinue(int total, List<Attempt> attempts) {
        if (attempts.size() == total || Thread.currentThread().isInterrupted()) {
            return false;
        }
        if (!attempts.isEmpty() && attempts.getLast().stop()) {
            return false;
        }
        return (attempts.isEmpty() || waitForNextRequest()) && !Thread.currentThread().isInterrupted();
    }

    private Attempt refreshTarget(PriceRefreshTarget target) {
        long started = System.nanoTime();
        try {
            return refreshInspected(target, SearchInspection.inspect(client.search(target.productCode()),
                target.vendorItemId()), started);
        } catch (CoupangApiException exception) {
            return apiFailure(target, exception, started);
        } catch (DataAccessException | CannotCreateTransactionException exception) {
            return databaseFailure(target, exception, started);
        }
    }

    private Attempt refreshInspected(PriceRefreshTarget target, SearchInspection inspection, long started) {
        String reason = inspection.deferralReason();
        if (reason != null) {
            return defer(target, reason, started, inspection);
        }
        long price = inspection.matches().getFirst().productPrice();
        PriceRefreshResult result = offers.refreshCoupangPrice(target, price, LocalDate.now(clock));
        return recordSaved(target, result, price, started, inspection);
    }

    private Attempt defer(PriceRefreshTarget target, String reason, long started, SearchInspection inspection) {
        if (target.status() == OfferStatus.HIDDEN && "NO_MATCH".equals(reason)) {
            resultLog(target, "HIDDEN_UNCHANGED", "NONE", started, inspection)
                .log("쿠팡 숨김 상품 유지: 검색 후보에 기준 ID 없음");
            return Attempt.HIDDEN_UNCHANGED;
        }
        // 검색 상위 후보에 없다는 사실만으로 판매 종료나 다른 상품이라고 판단할 수 없다.
        resultLog(target, reason, "NONE", started, inspection).log("쿠팡 가격 갱신 보류: 판매처 링크 확인 필요");
        return Attempt.DEFERRED;
    }

    private Attempt recordSaved(PriceRefreshTarget target, PriceRefreshResult result, long price, long started,
                                SearchInspection inspection) {
        if (result == PriceRefreshResult.SKIPPED) {
            return defer(target, "SKIPPED", started, inspection);
        }
        boolean priceChanged = target.price() != price;
        logSaved(target, result, price, started, inspection);
        return new Attempt(priceChanged, !priceChanged, result == PriceRefreshResult.RESTORED, false, false);
    }

    private void logSaved(PriceRefreshTarget target, PriceRefreshResult result, long price, long started,
                          SearchInspection inspection) {
        // 서비스 트랜잭션이 커밋된 뒤에만 변경 알림의 근거를 남긴다.
        resultLog(target, result.name(), result.name(), started, inspection)
            .addKeyValue("price_refresh.price_changed", target.price() != price)
            .addKeyValue("price_refresh.previous_price", target.price())
            .addKeyValue("price_refresh.current_price", price)
            .log("쿠팡 판매상품 가격 확인");
    }

    private Attempt apiFailure(PriceRefreshTarget target, CoupangApiException exception, long started) {
        LoggingEventBuilder event = targetLog(log.atWarn(), target, "API_FAILURE", "NONE", started)
            .addKeyValue("error.code", exception.getReason().name())
            .addKeyValue("error.type", exception.getClass().getName());
        if (exception.getResponseCode() != null) {
            event.addKeyValue("coupang.r_code", safeResponseCode(exception.getResponseCode()));
        }
        event.log("쿠팡 가격 조회 실패로 갱신 보류");
        boolean stop = exception.getReason() == CoupangApiException.Reason.AUTHENTICATION_FAILED
            || exception.getReason() == CoupangApiException.Reason.RATE_LIMITED;
        return new Attempt(false, false, false, stop, false);
    }

    private String safeResponseCode(String responseCode) {
        // 외부 값은 먼저 길이를 제한하고 제어문자 등을 정제한다. 원문은 예외 필드에만 보존한다.
        String limited = responseCode.substring(0, Math.min(responseCode.length(), 64));
        String sanitized = limited.replaceAll("[^A-Za-z0-9._-]", "?");
        if (responseCode.length() > 64) {
            return sanitized + "...";
        }
        return sanitized;
    }

    private Attempt databaseFailure(PriceRefreshTarget target, RuntimeException exception, long started) {
        targetLog(databaseFailureEvent(exception), target, "DB_FAILURE", "NONE", started)
            .log("쿠팡 가격 저장 실패로 해당 상품 갱신 취소");
        return new Attempt(false, false, false, exception instanceof CannotCreateTransactionException, false);
    }

    private LoggingEventBuilder databaseFailureEvent(RuntimeException exception) {
        if (!(exception instanceof CannotCreateTransactionException)) {
            return log.atWarn().addKeyValue("error.type", exception.getClass().getName());
        }
        // DB 예외의 메시지·원인에는 접속 정보가 섞일 수 있어, 안전한 메시지와 실패 위치만 남긴다.
        CannotCreateTransactionException logged = new CannotCreateTransactionException("쿠팡 가격 저장 트랜잭션 시작 실패");
        logged.setStackTrace(exception.getStackTrace());
        return log.atError().setCause(logged);
    }

    private Summary summarize(List<Attempt> attempts, int total) {
        int updated = (int) attempts.stream().filter(Attempt::updated).count();
        int unchanged = (int) attempts.stream().filter(Attempt::unchanged).count();
        int restored = (int) attempts.stream().filter(Attempt::restored).count();
        int hiddenUnchanged = (int) attempts.stream().filter(Attempt::hiddenUnchanged).count();
        return new Summary(updated, unchanged, attempts.size() - updated - unchanged - hiddenUnchanged,
            total - attempts.size(), 0, restored, hiddenUnchanged);
    }

    private void logNotAttempted(PriceRefreshTarget target) {
        targetLog(log.atInfo(), target, "NOT_ATTEMPTED", "NONE", System.nanoTime())
            .log("쿠팡 가격 최신화 중단으로 미시도");
    }

    private void logSummary(Summary summary, long started) {
        summaryCounts(summary).addKeyValue("price_refresh.outcome", summary.outcome())
            .addKeyValue("price_refresh.success", summary.success())
            .addKeyValue("price_refresh.total", summary.total())
            .addKeyValue("event.duration", System.nanoTime() - started)
            .log("쿠팡 가격 최신화 종료");
    }

    private LoggingEventBuilder summaryCounts(Summary summary) {
        return log.atInfo().addKeyValue("price_refresh.updated", summary.updated())
            .addKeyValue("price_refresh.unchanged", summary.unchanged())
            .addKeyValue("price_refresh.deferred", summary.deferred())
            .addKeyValue("price_refresh.not_attempted", summary.notAttempted())
            .addKeyValue("price_refresh.hidden", summary.hidden())
            .addKeyValue("price_refresh.restored", summary.restored())
            .addKeyValue("price_refresh.hidden_unchanged", summary.hiddenUnchanged());
    }

    private void logFailure(RuntimeException exception, long started) {
        log.atError().addKeyValue("price_refresh.outcome", "FAILED")
            .addKeyValue("event.duration", System.nanoTime() - started).setCause(exception)
            .log("쿠팡 가격 최신화 실행 실패");
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
        return offerLog(event, target).addKeyValue("search.keyword", target.productCode())
            .addKeyValue("server.address", "api-gateway.coupang.com")
            .addKeyValue("event.duration", System.nanoTime() - started)
            .addKeyValue("price_refresh.result", result)
            .addKeyValue("price_refresh.action", action);
    }

    private LoggingEventBuilder offerLog(LoggingEventBuilder event, PriceRefreshTarget target) {
        return event.addKeyValue("offer.id", target.offerId())
            .addKeyValue("offer.url", target.purchaseUrl())
            .addKeyValue("offer.vendor_item_id", target.vendorItemId())
            .addKeyValue("product.id", target.productId())
            .addKeyValue("product.code", target.productCode())
            .addKeyValue("product.url", publicBaseUrl + "/products/" + target.productId());
    }

    private record Attempt(boolean updated, boolean unchanged, boolean restored, boolean stop, boolean hiddenUnchanged) {

        private static final Attempt DEFERRED = new Attempt(false, false, false, false, false);
        private static final Attempt HIDDEN_UNCHANGED = new Attempt(false, false, false, false, true);
    }

    private record Candidate(CoupangProductSearchClient.Product product, CoupangProductUrl identifiers) {

        static Candidate parse(CoupangProductSearchClient.Product product) {
            return new Candidate(product, CoupangProductUrl.parse(product.productUrl()).orElse(null));
        }

        boolean invalidUrl() {
            return identifiers == null;
        }

        boolean missingId() {
            return identifiers != null && identifiers.vendorItemId() == null;
        }

        String vendorItemId() {
            if (identifiers == null) {
                return null;
            }
            return identifiers.vendorItemId();
        }
    }

    private record SearchInspection(int candidateCount, int invalidUrls, int missingIds, List<String> vendorItemIds,
                                    List<CoupangProductSearchClient.Product> matches) {

        static SearchInspection inspect(List<CoupangProductSearchClient.Product> products, String expectedId) {
            List<Candidate> candidates = products.stream().map(Candidate::parse).toList();
            List<String> ids = candidates.stream().map(Candidate::vendorItemId).filter(Objects::nonNull).toList();
            List<CoupangProductSearchClient.Product> matches = candidates.stream()
                .filter(candidate -> expectedId.equals(candidate.vendorItemId())).map(Candidate::product).toList();
            return new SearchInspection(candidates.size(),
                (int) candidates.stream().filter(Candidate::invalidUrl).count(),
                (int) candidates.stream().filter(Candidate::missingId).count(), ids, matches);
        }

        String deferralReason() {
            if (matches.isEmpty()) {
                return noMatchReason();
            }
            if (matches.stream().anyMatch(product -> product.productPrice() == null || product.productPrice() <= 0)) {
                return "INVALID_PRICE";
            }
            return priceConflictReason();
        }

        private String priceConflictReason() {
            if (matches.stream().map(CoupangProductSearchClient.Product::productPrice).distinct().count() == 1) {
                return null;
            }
            return "CONFLICTING_PRICES";
        }

        String noMatchReason() {
            if (candidateCount == 0) {
                return "NO_SEARCH_RESULTS";
            }
            if (invalidUrls + missingIds > 0) {
                return "IDENTIFIER_UNAVAILABLE";
            }
            return "NO_MATCH";
        }
    }

    // hidden은 이전 로그와의 호환을 위해 유지하며, 자동 숨김을 하지 않으므로 항상 0이다.
    // restored는 이미 숨겨진 상품을 다시 확인한 경우로 updated + unchanged에 포함된다.
    // hiddenUnchanged는 정상적인 숨김 유지이며, 가격 확인 성공에는 포함하지 않는다.
    public record Summary(int updated, int unchanged, int deferred, int notAttempted, int hidden, int restored,
                          int hiddenUnchanged) {

        public int success() {
            return updated + unchanged;
        }

        public int total() {
            return success() + hiddenUnchanged + deferred + notAttempted;
        }

        public String outcome() {
            if (total() == 0) {
                return "NO_TARGETS";
            }
            if (success() + hiddenUnchanged == 0) {
                return "FAILED";
            }
            if (deferred + notAttempted == 0) {
                return "SUCCESS";
            }
            return "PARTIAL";
        }

        public int exitCode() {
            if ("SUCCESS".equals(outcome())) {
                return 0;
            }
            return 2;
        }
    }

    @FunctionalInterface
    interface Pause {
        void sleep(long millis) throws InterruptedException;
    }
}
