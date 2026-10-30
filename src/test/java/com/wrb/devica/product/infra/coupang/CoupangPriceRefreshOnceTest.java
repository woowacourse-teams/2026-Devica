package com.wrb.devica.product.infra.coupang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.wrb.devica.product.service.ProductOfferService;
import java.net.http.HttpClient;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

class CoupangPriceRefreshOnceTest {

    @ParameterizedTest
    @CsvSource({"1,0,0,0", "0,1,0,0", "1,0,1,2", "0,0,1,2", "0,0,0,2"})
    void 결과_종료코드를_보존하고_종료시_컨텍스트_자원을_닫는다(int updated, int hidden, int deferred, int expected) {
        // given
        CoupangPriceRefreshJob job = mock(CoupangPriceRefreshJob.class);
        when(job.refreshPrices()).thenReturn(new CoupangPriceRefreshJob.Summary(updated, 0, deferred, 0, 0, 0, hidden));
        AtomicBoolean closed = new AtomicBoolean();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CoupangPriceRefreshOnce.class, () -> new CoupangPriceRefreshOnce(job));
            context.registerBean("resource", AutoCloseable.class, () -> () -> closed.set(true),
                definition -> definition.setDestroyMethodName("close"));
            context.refresh();

            // when
            context.getBean(CoupangPriceRefreshOnce.class).run(new DefaultApplicationArguments());
            int code = SpringApplication.exit(context);

            // then (System.exit는 호출하지 않는다.)
            assertThat(code).isEqualTo(expected);
            assertThat(context.isActive()).isFalse();
            assertThat(closed).isTrue();
            verify(job).refreshPrices();
            verifyNoMoreInteractions(job);
        }
    }

    @Test
    void 실행_예외는_종료코드_1이고_컨텍스트를_닫는다() {
        // given
        CoupangPriceRefreshJob job = mock(CoupangPriceRefreshJob.class);
        when(job.refreshPrices()).thenThrow(new IllegalStateException("already logged by job"));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CoupangPriceRefreshOnce.class, () -> new CoupangPriceRefreshOnce(job));
            context.refresh();

            // when
            context.getBean(CoupangPriceRefreshOnce.class).run(new DefaultApplicationArguments());

            // then
            assertThat(SpringApplication.exit(context)).isEqualTo(1);
            assertThat(context.isActive()).isFalse();
            verify(job).refreshPrices();
            verifyNoMoreInteractions(job);
        }
    }

    @Test
    void Spring_기동시_공통_Job을_정확히_한번_실행하고_HTTP_자원도_닫는다() {
        // given: DB 서비스를 대체하므로 실제 DB나 쿠팡에는 접속하지 않는다.
        ProductOfferService offers = mock(ProductOfferService.class);
        when(offers.findCoupangPriceRefreshTargets()).thenReturn(List.of());
        SpringApplication application = new SpringApplication(CoupangPriceRefreshConfig.class);
        application.addInitializers(context -> context.getBeanFactory().registerSingleton("offers", offers));

        // when
        try (var context = application.run(
            "--spring.config.location=optional:classpath:/empty-run-once.yml",
            "--spring.profiles.active=local", "--spring.main.web-application-type=none",
            "--spring.datasource.url=jdbc:mysql://localhost:13306/devica?characterEncoding=UTF-8&serverTimezone=Asia/Seoul",
            "--coupang.price-refresh.run-once=true", "--coupang.price-refresh.enabled=true",
            "--coupang.price-refresh.cron=-", "--coupang.access-key=test-access", "--coupang.secret-key=test-secret")) {
            // then
            assertThat(context.getBeansOfType(CoupangPriceRefreshJob.class)).hasSize(1);
            assertThat(context.getBeansOfType(CoupangPriceRefreshOnce.class)).hasSize(1);
            assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
            verify(offers).findCoupangPriceRefreshTargets();
            verifyNoMoreInteractions(offers);
            HttpClient client = context.getBean(HttpClient.class);
            assertThat(SpringApplication.exit(context)).isEqualTo(2);
            assertThat(context.isActive()).isFalse();
            assertThat(client.isTerminated()).isTrue();
        }
    }
}
