package com.wrb.devica.product.infra.coupang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SimpleCommandLinePropertySource;

class CoupangPriceRefreshLocalTest {

    @Test
    void 기존_키_파일을_기본으로_사용하고_경로를_바꿀_수_있다() {
        assertThat(CoupangPriceRefreshLocal.envFile(new String[]{})).isEqualTo(Path.of("coupang.local.env"));
        assertThat(CoupangPriceRefreshLocal.envFile(new String[]{"--env-file=/tmp/keys.env"}))
            .isEqualTo(Path.of("/tmp/keys.env"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"--spring.profiles.active=prod", "--spring.datasource.url=remote", "--env-file="})
    void 환경을_바꾸는_임의의_실행_옵션은_받지_않는다(String argument) {
        assertThatThrownBy(() -> CoupangPriceRefreshLocal.envFile(new String[]{argument}))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "65536", "abc", "3306/remote", ""})
    void 로컬_DB_포트가_아니면_앱을_기동하지_않는다(String port) {
        assertThatThrownBy(() -> CoupangPriceRefreshLocal.localArguments(Map.of("DB_PORT", port), Path.of("run.log")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 셸에_서버_설정이_남아_있어도_로컬_설정을_쓰고_예약_작업을_만들지_않는다() {
        String[] arguments = CoupangPriceRefreshLocal.localArguments(Map.of("DB_PORT", "13306"), Path.of("run.log"));
        new ApplicationContextRunner().withUserConfiguration(CoupangPriceRefreshConfig.class)
            .withPropertyValues("spring.profiles.active=prod", "spring.datasource.url=jdbc:mysql://remote/devica",
                "spring.datasource.hikari.jdbc-url=jdbc:mysql://remote/devica", "coupang.price-refresh.enabled=true")
            .withInitializer(context -> context.getEnvironment().getPropertySources()
                .addFirst(new SimpleCommandLinePropertySource(arguments)))
            .run(context -> {
                assertThat(context).hasNotFailed().doesNotHaveBean(CoupangPriceRefreshJob.class);
                var environment = context.getEnvironment();
                assertThat(environment.getActiveProfiles()).containsExactly("local");
                assertThat(environment.getProperty("spring.datasource.url"))
                    .startsWith("jdbc:mysql://localhost:13306/devica?");
                assertThat(environment.getProperty("spring.datasource.hikari.jdbc-url"))
                    .isEqualTo(environment.getProperty("spring.datasource.url"));
                assertThat(environment.getProperty("spring.main.web-application-type")).isEqualTo("none");
                assertThat(environment.getProperty("coupang.price-refresh.cron")).isEqualTo("-");
                assertThat(environment.getProperty("logging.file.name"))
                    .isEqualTo(Path.of("run.log").toAbsolutePath().toString());
            });
    }
}
