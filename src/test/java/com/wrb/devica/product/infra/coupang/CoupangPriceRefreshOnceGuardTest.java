package com.wrb.devica.product.infra.coupang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CoupangPriceRefreshOnceGuardTest {

    private static final String LOCAL_URL =
        "jdbc:mysql://localhost:13306/devica?characterEncoding=UTF-8&serverTimezone=Asia/Seoul";
    private static final AtomicBoolean CREATED = new AtomicBoolean();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "spring.profiles.active|prod", "spring.profiles.active|local,prod",
        "spring.main.web-application-type|servlet", "coupang.price-refresh.cron|0 0 4 * * *",
        "spring.datasource.url|jdbc:mysql://remote:3306/devica",
        "spring.datasource.hikari.jdbcUrl|jdbc:mysql://remote:3306/devica",
        "spring.flyway.url|jdbc:mysql://remote:3306/devica",
        "spring.datasource.jndiName|java:comp/env/remote",
        "spring.datasource.hikari.dataSourceClassName|com.mysql.cj.jdbc.MysqlDataSource",
        "spring.datasource.hikari.dataSourceJNDI|java:comp/env/remote",
        "spring.datasource.hikari.dataSourceProperties.serverName|remote",
        "spring.datasource.type|com.example.OtherDataSource",
        "spring.datasource.driverClassName|com.example.OtherDriver"
    })
    void 위험한_수동_설정은_DB나_웹_빈_생성전에_거절한다(String key, String value) {
        // given
        Map<String, String> properties = properties();
        properties.put(key, value);
        CREATED.set(false);

        // when & then: spring.factories 등록도 실제 SpringApplication으로 검증한다.
        assertThatThrownBy(() -> application().run(arguments(properties)))
            .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("쿠팡 수동 가격 최신화:")
            .hasMessageNotContaining(value);
        assertThat(CREATED).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "65536", "abc", "3306/remote"})
    void 잘못된_포트는_빈_생성전에_거절한다(String port) {
        // given
        Map<String, String> properties = properties();
        properties.put("spring.datasource.url", LOCAL_URL.replace("13306", port));
        CREATED.set(false);

        // when & then
        assertThatThrownBy(() -> application().run(arguments(properties))).isInstanceOf(IllegalStateException.class);
        assertThat(CREATED).isFalse();
    }

    @Test
    void 같은_로컬_DB를_가리키는_Hikari와_Flyway_설정은_허용한다() {
        // given
        Map<String, String> properties = properties();
        properties.put("spring.datasource.hikari.jdbcUrl", LOCAL_URL);
        properties.put("spring.flyway.url", LOCAL_URL);
        CREATED.set(false);

        // when & then
        try (var context = application().run(arguments(properties))) {
            assertThat(context.isActive()).isTrue();
            assertThat(CREATED).isTrue();
        }
    }

    @Test
    void 수동실행이_아닌_서버_환경에는_로컬_제한을_적용하지_않는다() {
        // given
        Map<String, String> properties = properties();
        properties.put("coupang.price-refresh.run-once", "false");
        properties.put("spring.profiles.active", "prod");
        properties.put("spring.datasource.url", "jdbc:mysql://remote:3306/devica");
        CREATED.set(false);

        // when & then: 실제 DB 빈은 없고 기동 검증용 빈만 사용한다.
        try (var context = application().run(arguments(properties))) {
            assertThat(context.isActive()).isTrue();
            assertThat(CREATED).isTrue();
        }
    }

    private SpringApplication application() {
        return new SpringApplication(StartupMarker.class);
    }

    private Map<String, String> properties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("spring.config.location", "optional:classpath:/empty-run-once.yml");
        properties.put("spring.profiles.active", "local");
        properties.put("spring.main.web-application-type", "none");
        properties.put("spring.datasource.url", LOCAL_URL);
        properties.put("coupang.price-refresh.run-once", "true");
        properties.put("coupang.price-refresh.cron", "-");
        return properties;
    }

    private String[] arguments(Map<String, String> properties) {
        return properties.entrySet().stream().map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
            .toArray(String[]::new);
    }

    @Configuration(proxyBeanMethods = false)
    static class StartupMarker {
        @Bean
        Object databaseStartupMarker() {
            CREATED.set(true);
            return new Object();
        }
    }
}
