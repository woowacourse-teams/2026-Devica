package com.wrb.devica.product.infra.coupang;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring/DB를 기동하지 않는 수동 조회 도구. ID 일치는 가격·사양 검증 완료를 뜻하지 않는다.
 */
public final class CoupangPriceProbe {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Set<String> OPTIONS = Set.of("env-file", "targets", "output");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        .withZone(ZoneOffset.UTC);

    private CoupangPriceProbe() {
    }

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("조회 검증이 중단되었습니다.");
            System.exit(1);
        } catch (IOException exception) {
            System.err.println("입력 파일 읽기 또는 보고서 저장에 실패했습니다. 경로와 권한을 확인하세요.");
            System.exit(1);
        } catch (IllegalArgumentException exception) {
            System.err.println("실행 옵션, 대상 JSON 또는 COUPANG_ACCESS_KEY / COUPANG_SECRET_KEY 설정을 확인하세요.");
            System.err.println("지원하는 옵션: --env-file=경로 --targets=경로 --output=경로");
            System.exit(1);
        } catch (RuntimeException exception) {
            // 키나 원문 응답이 예외 상세에 섞일 수 있어 스택 트레이스를 출력하지 않는다.
            System.err.println("조회 검증을 완료하지 못했습니다. 입력 JSON과 실행 환경을 확인하세요.");
            System.exit(1);
        }
    }

    private static int run(String[] args) throws IOException, InterruptedException {
        Map<String, String> options = options(args);
        Clock clock = Clock.systemUTC();
        Path envFile = options.containsKey("env-file") ? Path.of(options.get("env-file")) : null;
        List<Target> targets = readTargets(options.get("targets"));
        CoupangHmacSigner signer = CoupangProbeCredentials.signer(System.getenv(), envFile, clock);
        Path output = options.containsKey("output") ? Path.of(options.get("output"))
            : Path.of("build/reports/coupang-price-probe-" + FILE_TIME.format(clock.instant()) + ".json");

        try (HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()) {
            JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(httpClient);
            requests.setReadTimeout(Duration.ofSeconds(10));
            CoupangProductSearchClient client = new CoupangProductSearchClient(
                RestClient.builder().requestFactory(requests).build(), signer);
            List<Result> results = new ArrayList<>();
            String stopReason = null;
            for (Target target : targets) {
                if (stopReason != null) {
                    results.add(new Result(target, clock.instant(), "NOT_ATTEMPTED", stopReason, List.of()));
                    continue;
                }
                if (!results.isEmpty()) {
                    Thread.sleep(2100);
                }
                Result result = inspect(target, client, clock);
                results.add(result);
                System.out.println(target.productCode() + ": " + result.outcome()
                    + (result.failureReason() == null ? "" : " (" + result.failureReason() + ")"));
                if (CoupangApiException.Reason.RATE_LIMITED.name().equals(result.failureReason())
                    || CoupangApiException.Reason.AUTHENTICATION_FAILED.name().equals(result.failureReason())) {
                    stopReason = result.failureReason();
                }
            }
            Files.createDirectories(output.toAbsolutePath().getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), new Report(
                "MANUAL_VERIFICATION_REQUIRED", false, results));
            System.out.println("보고서: " + output.toAbsolutePath());
            System.out.println("옵션 ID가 일치해도 사양·선택 옵션의 가격·일반 판매가 기준은 별도로 확인해야 합니다.");
            return results.stream().anyMatch(result -> "API_FAILURE".equals(result.outcome())) ? 2 : 0;
        }
    }

    static Result inspect(Target target, CoupangProductSearchClient client, Clock clock) {
        CoupangProductUrl expected = CoupangProductUrl.parse(target.purchaseUrl()).orElseThrow(
            () -> new IllegalArgumentException("검증 대상의 쿠팡 구매 URL을 확인하세요."));
        try {
            List<Candidate> candidates = client.search(target.keyword()).stream()
                .map(product -> candidate(expected, product))
                .toList();
            return new Result(target, clock.instant(), candidates.isEmpty() ? "NO_SEARCH_RESULTS" : "RETRIEVED",
                null, candidates);
        } catch (CoupangApiException exception) {
            return new Result(target, clock.instant(), "API_FAILURE", exception.getReason().name(), List.of());
        }
    }

    private static Candidate candidate(CoupangProductUrl expected, CoupangProductSearchClient.Product product) {
        CoupangProductUrl identifiers = CoupangProductUrl.parse(product.productUrl()).orElse(null);
        CoupangProductUrl.OptionMatch match = identifiers == null
            ? CoupangProductUrl.OptionMatch.INVALID_PRODUCT_URL : expected.compareOption(identifiers);
        String priceCheck = product.productPrice() == null || product.productPrice() <= 0
            ? "MISSING_OR_INVALID_PRICE" : "REQUIRES_PRICE_VERIFICATION";
        return new Candidate(product, identifiers, match, priceCheck);
    }

    private static List<Target> readTargets(String file) throws IOException {
        try (InputStream input = file == null
            ? CoupangPriceProbe.class.getResourceAsStream("/coupang/price-probe-targets.json")
            : Files.newInputStream(Path.of(file))) {
            if (input == null) {
                throw new IllegalArgumentException("검증 대상 JSON 파일을 찾지 못했습니다.");
            }
            List<Target> targets = MAPPER.readerForListOf(Target.class).readValue(input);
            if (targets == null || targets.isEmpty() || targets.size() > 20 || targets.contains(null)) {
                throw new IllegalArgumentException("검증 대상은 1~20개를 지정해야 합니다.");
            }
            for (Target target : targets) {
                if (target.productCode() == null || target.productCode().isBlank()
                    || target.keyword() == null || target.keyword().isBlank()
                    || target.expectedConfiguration() == null || target.expectedConfiguration().isBlank()) {
                    throw new IllegalArgumentException("대상의 제품 코드, 검색어, 기대 사양을 입력해야 합니다.");
                }
                CoupangProductUrl identifiers = CoupangProductUrl.parse(target.purchaseUrl()).orElseThrow(
                    () -> new IllegalArgumentException("대상의 구매 URL이 올바르지 않습니다."));
                if (identifiers.itemId() == null || identifiers.vendorItemId() == null) {
                    throw new IllegalArgumentException("대상의 구매 URL에 itemId와 vendorItemId가 필요합니다.");
                }
            }
            return targets;
        }
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            String[] pair = arg.startsWith("--") ? arg.substring(2).split("=", 2) : new String[0];
            if (pair.length != 2 || !OPTIONS.contains(pair[0]) || pair[1].isBlank()
                || options.putIfAbsent(pair[0], pair[1]) != null) {
                throw new IllegalArgumentException("지원하는 옵션: --env-file=경로 --targets=경로 --output=경로");
            }
        }
        return options;
    }

    public record Target(String productCode, String keyword, String purchaseUrl, String expectedConfiguration) {
    }

    public record Candidate(CoupangProductSearchClient.Product response, CoupangProductUrl urlIdentifiers,
                            CoupangProductUrl.OptionMatch optionMatch, String priceCheck) {
    }

    public record Result(Target target, Instant observedAt, String outcome, String failureReason,
                         List<Candidate> candidates) {
    }

    public record Report(String verificationStatus, boolean databaseModified, List<Result> results) {
    }
}
