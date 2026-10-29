package com.wrb.devica.product.infra.coupang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.wrb.devica.product.service.ProductOfferService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;

class CoupangPriceRefreshConfigTest {

    private final ProductOfferService offers = mock(ProductOfferService.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(CoupangPriceRefreshConfig.class)
        .withBean(ProductOfferService.class, () -> offers);

    @Test
    void 기본값에서는_API_키_없이_서버를_시작하고_자동_갱신을_등록하지_않는다() {
        // when & then
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(CoupangPriceRefreshJob.class)
                .doesNotHaveBean(CoupangProductSearchClient.class).doesNotHaveBean(CoupangPriceRefreshOnce.class);
            verifyNoInteractions(offers);
        });
    }

    @Test
    void 활성화하면_일일_작업을_등록하지만_기동_즉시_실행하지_않는다() {
        // when & then
        contextRunner.withPropertyValues("coupang.price-refresh.enabled=true",
            "coupang.access-key=test-access", "coupang.secret-key=test-secret",
            "coupang.price-refresh.cron=0 0 4 * * *")
            .run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(CoupangPriceRefreshJob.class)
                    .doesNotHaveBean(CoupangPriceRefreshOnce.class);
                var tasks = context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
                assertThat(tasks).hasSize(1);
                assertThat(((CronTask) tasks.iterator().next().getTask()).getExpression()).isEqualTo("0 0 4 * * *");
                verifyNoInteractions(offers);
            });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 수동_실행은_자동_갱신_설정과_무관하게_공통_빈만_만들고_예약하지_않는다(boolean enabled) {
        // given & when
        contextRunner.withPropertyValues("coupang.price-refresh.run-once=true",
            "coupang.price-refresh.enabled=" + enabled,
            "coupang.access-key=test-access", "coupang.secret-key=test-secret")
            .run(context -> {
                // then (ApplicationContextRunner는 ApplicationRunner를 실행하지 않는다.)
                assertThat(context).hasNotFailed().hasSingleBean(CoupangPriceRefreshJob.class)
                    .hasSingleBean(CoupangProductSearchClient.class).hasSingleBean(CoupangPriceRefreshOnce.class)
                    .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
                verifyNoInteractions(offers);
            });
    }

    @ParameterizedTest
    @ValueSource(strings = {"enabled", "run-once"})
    void 갱신을_켰는데_키가_없으면_잘못된_설정으로_기동을_실패시킨다(String mode) {
        // when & then
        contextRunner.withPropertyValues("coupang.price-refresh." + mode + "=true").run(context ->
            assertThat(context).hasFailed().getFailure().hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("쿠팡 Access Key와 Secret Key를 설정해야 합니다."));
    }
}
