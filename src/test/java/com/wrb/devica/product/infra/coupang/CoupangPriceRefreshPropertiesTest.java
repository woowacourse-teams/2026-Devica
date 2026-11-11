package com.wrb.devica.product.infra.coupang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.wrb.devica.product.service.ProductOfferService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class CoupangPriceRefreshPropertiesTest {

    @TempDir
    Path directory;

    private final ProductOfferService offers = mock(ProductOfferService.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withInitializer(context -> {
            // 개발자의 실제 환경변수·키 파일 대신 테스트 입력과 실제 application.yml만 사용한다.
            var sources = context.getEnvironment().getPropertySources();
            sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        })
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues("spring.config.location=" + Path.of("src/main/resources/application.yml").toAbsolutePath().toUri(),
            "spring.profiles.active=key-binding-test", "coupang.price-refresh.cron=-")
        .withUserConfiguration(CoupangPriceRefreshConfig.class)
        .withBean(ProductOfferService.class, () -> offers);

    @Test
    void YAML의_기본_비활성_설정에서는_키_없이_기동한다() {
        // when & then
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(CoupangPriceRefreshJob.class);
            assertThat(context.getEnvironment().getPropertySources()).anySatisfy(source ->
                assertThat(source.getName()).contains("src/main/resources/application.yml"));
            assertThat(context.getEnvironment().getProperty("coupang.access-key")).isEmpty();
            assertThat(context.getEnvironment().getProperty("coupang.secret-key")).isEmpty();
            verifyNoInteractions(offers);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"enabled", "run-once"})
    void YAML이_기존_대문자_입력을_공통_클라이언트_설정으로_연결한다(String mode) {
        // given & when
        contextRunner.withPropertyValues("coupang.price-refresh." + mode + "=true",
            "COUPANG_ACCESS_KEY=test-access", "COUPANG_SECRET_KEY=test-secret")
            .run(context -> {
                // then
                assertThat(context).hasNotFailed().hasSingleBean(CoupangProductSearchClient.class);
                assertThat(context.getEnvironment().getProperty("coupang.access-key")).isEqualTo("test-access");
                assertThat(context.getEnvironment().getProperty("coupang.secret-key")).isEqualTo("test-secret");
                verifyNoInteractions(offers);
            });
    }

    @ParameterizedTest
    @CsvSource({"enabled,COUPANG_ACCESS_KEY=test-access", "enabled,COUPANG_SECRET_KEY=test-secret",
        "run-once,COUPANG_ACCESS_KEY=test-access", "run-once,COUPANG_SECRET_KEY=test-secret"})
    void YAML을_읽어도_활성_모드에_키_하나가_없으면_기동하지_않는다(String mode, String presentKey) {
        // when & then
        contextRunner.withPropertyValues("coupang.price-refresh." + mode + "=true", presentKey).run(context ->
            assertThat(context).hasFailed().getFailure().hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("쿠팡 Access Key와 Secret Key를 설정해야 합니다."));
        verifyNoInteractions(offers);
    }

    @Test
    void 로컬_properties_파일의_기존_키_이름으로_일회_실행을_구성한다() throws IOException {
        // given
        Path file = keyFile();

        // when & then (ApplicationContextRunner는 ApplicationRunner를 실행하지 않는다.)
        contextRunner.withPropertyValues("spring.config.import=" + file.toUri() + "[.properties]",
            "coupang.price-refresh.run-once=true").run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(CoupangPriceRefreshOnce.class);
                assertThat(context.getEnvironment().getProperty("coupang.access-key")).isEqualTo("file-access");
                assertThat(context.getEnvironment().getProperty("coupang.secret-key")).isEqualTo("file-secret");
                verifyNoInteractions(offers);
            });
    }

    @Test
    void 환경변수가_로컬_키_파일보다_우선한다() throws IOException {
        // given
        Path file = keyFile();

        // when & then
        contextRunner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
            new SystemEnvironmentPropertySource("testEnvironment", Map.of(
                "COUPANG_ACCESS_KEY", "environment-access", "COUPANG_SECRET_KEY", "environment-secret"))))
            .withPropertyValues("spring.config.import=" + file.toUri() + "[.properties]",
                "coupang.price-refresh.run-once=true").run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CoupangPriceRefreshOnce.class);
                    assertThat(context.getEnvironment().getProperty("coupang.access-key"))
                        .isEqualTo("environment-access");
                    assertThat(context.getEnvironment().getProperty("coupang.secret-key"))
                        .isEqualTo("environment-secret");
                    verifyNoInteractions(offers);
                });
    }

    private Path keyFile() throws IOException {
        return Files.writeString(directory.resolve("coupang-test.env"),
            "COUPANG_ACCESS_KEY=file-access\nCOUPANG_SECRET_KEY=file-secret\n");
    }
}
