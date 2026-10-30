package com.wrb.devica.product.infra.coupang;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.web.context.WebApplicationContext;

/** ConfigData 적용 후, DB·Flyway·웹 서버 빈이 만들어지기 전에 수동 실행 환경을 확인한다. */
public final class CoupangPriceRefreshOnceGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Pattern LOCAL_URL = Pattern.compile(
        "jdbc:mysql://localhost:([0-9]{1,5})/devica\\?characterEncoding=UTF-8&serverTimezone=Asia/Seoul");

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();
        Binder binder = Binder.get(environment);
        if (!binder.bind("coupang.price-refresh.run-once", Boolean.class).orElse(false)) {
            return;
        }
        String[] profiles = environment.getActiveProfiles().length == 0
            ? environment.getDefaultProfiles() : environment.getActiveProfiles();
        require(Arrays.equals(profiles, new String[]{"local"}), "local 프로파일만 허용합니다.");
        require("none".equalsIgnoreCase(value(binder, "spring.main.web-application-type"))
            && !(context instanceof WebApplicationContext), "웹 서버를 비활성화해야 합니다.");
        require("-".equals(value(binder, "coupang.price-refresh.cron")), "예약 실행을 비활성화해야 합니다.");

        String url = value(binder, "spring.datasource.url");
        var matcher = LOCAL_URL.matcher(url);
        require(matcher.matches(), "기존 로컬 devica DB 주소만 허용합니다.");
        int port = Integer.parseInt(matcher.group(1));
        require(port >= 1 && port <= 65535, "로컬 DB 포트를 확인하세요.");
        for (String key : new String[]{"spring.datasource.hikari.jdbc-url", "spring.flyway.url"}) {
            String override = value(binder, key);
            require(override.isBlank() || override.equals(url), "별도 DB 주소를 사용할 수 없습니다.");
        }
        for (String key : new String[]{"spring.datasource.jndi-name", "spring.datasource.hikari.data-source-class-name",
            "spring.datasource.hikari.data-source-jndi"}) {
            require(value(binder, key).isBlank(), "별도 DataSource를 사용할 수 없습니다.");
        }
        require(binder.bind("spring.datasource.hikari.data-source-properties", Bindable.mapOf(String.class, String.class))
            .orElse(Map.of()).isEmpty(), "별도 DataSource 속성을 사용할 수 없습니다.");
        for (String key : new String[]{"spring.datasource.driver-class-name", "spring.datasource.hikari.driver-class-name",
            "spring.flyway.driver-class-name"}) {
            String driver = value(binder, key);
            require(driver.isBlank() || driver.equals("com.mysql.cj.jdbc.Driver"), "기존 MySQL 드라이버만 허용합니다.");
        }
        String type = value(binder, "spring.datasource.type");
        require(type.isBlank() || type.equals("com.zaxxer.hikari.HikariDataSource"), "기존 DataSource만 허용합니다.");
    }

    private static String value(Binder binder, String name) {
        return binder.bind(name, String.class).orElse("");
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            // URL이나 설정값 자체에는 비밀정보가 있을 수 있으므로 출력하지 않는다.
            throw new IllegalStateException("쿠팡 수동 가격 최신화: " + message);
        }
    }
}
