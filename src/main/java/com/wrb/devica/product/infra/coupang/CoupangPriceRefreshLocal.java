package com.wrb.devica.product.infra.coupang;

import com.wrb.devica.DevicaApplication;
import com.wrb.devica.product.service.ProductOfferService;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 로컬 DB의 가격을 한 번 갱신하고 종료한다. 웹 서버와 예약 작업은 시작하지 않는다. */
public final class CoupangPriceRefreshLocal {

    private static final Clock CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        .withZone(CLOCK.getZone());

    private CoupangPriceRefreshLocal() {
    }

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("키 파일, COUPANG_ACCESS_KEY / COUPANG_SECRET_KEY, DB_PORT 설정을 확인하세요.");
            System.err.println("지원 옵션: --env-file=경로 (기본: coupang.local.env)");
            System.exit(1);
        } catch (RuntimeException exception) {
            System.err.println("가격 최신화를 완료하지 못했습니다. 실행 로그와 로컬 DB 연결을 확인하세요.");
            System.exit(1);
        }
    }

    private static int run(String[] args) throws IOException {
        CoupangHmacSigner signer = CoupangProbeCredentials.signer(System.getenv(), envFile(args), CLOCK);
        Path logFile = Path.of("build/reports/coupang-price-refresh-" + FILE_TIME.format(CLOCK.instant()) + ".log");
        String[] applicationArgs = localArguments(System.getenv(), logFile);
        SpringApplication application = new SpringApplication(DevicaApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        System.out.println("로컬 가격 최신화 로그: " + logFile.toAbsolutePath());
        try (HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
             ConfigurableApplicationContext context = application.run(applicationArgs)) {
            JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(httpClient);
            requests.setReadTimeout(Duration.ofSeconds(10));
            CoupangProductSearchClient client = new CoupangProductSearchClient(
                RestClient.builder().requestFactory(requests).build(), signer);
            CoupangPriceRefreshJob job = new CoupangPriceRefreshJob(client,
                context.getBean(ProductOfferService.class), CLOCK);
            return job.refreshPrices().exitCode();
        }
    }

    static Path envFile(String[] args) {
        if (args.length == 0) {
            return Path.of("coupang.local.env");
        }
        if (args.length == 1 && args[0].startsWith("--env-file=")
            && !args[0].substring("--env-file=".length()).isBlank()) {
            return Path.of(args[0].substring("--env-file=".length()));
        }
        throw new IllegalArgumentException("지원하지 않는 실행 옵션입니다.");
    }

    static String[] localArguments(Map<String, String> environment, Path logFile) {
        String port = environment.getOrDefault("DB_PORT", "3306");
        if (!port.matches("[0-9]{1,5}") || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535) {
            throw new IllegalArgumentException("로컬 DB 포트가 올바르지 않습니다.");
        }
        String url = "jdbc:mysql://localhost:" + port
            + "/devica?characterEncoding=UTF-8&serverTimezone=Asia/Seoul";
        return new String[]{
            "--spring.profiles.active=local",
            "--spring.profiles.include=",
            "--spring.main.web-application-type=none",
            // 셸에 dev/prod 설정이 남아 있어도 로컬 DB만 사용한다.
            "--spring.datasource.url=" + url,
            "--spring.datasource.hikari.jdbc-url=" + url,
            "--spring.datasource.username=root",
            "--spring.datasource.password=root",
            "--spring.datasource.hikari.username=root",
            "--spring.datasource.hikari.password=root",
            "--coupang.price-refresh.enabled=false",
            "--coupang.price-refresh.cron=-",
            "--spring.jpa.show-sql=false",
            "--logging.level.org.hibernate.orm.jdbc.bind=info",
            "--logging.pattern.correlation=[run:%X{price_refresh.run_id:-}] %kvp ",
            "--logging.structured.format.file=ecs",
            "--logging.file.name=" + logFile.toAbsolutePath()
        };
    }
}
