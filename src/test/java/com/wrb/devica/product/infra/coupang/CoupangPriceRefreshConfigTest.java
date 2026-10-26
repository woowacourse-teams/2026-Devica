package com.wrb.devica.product.infra.coupang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.wrb.devica.product.service.ProductOfferService;
import org.junit.jupiter.api.Test;
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
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(CoupangPriceRefreshJob.class)
                .doesNotHaveBean(CoupangProductSearchClient.class);
            verifyNoInteractions(offers);
        });
    }

    @Test
    void 활성화하면_일일_작업을_등록하지만_기동_즉시_실행하지_않는다() {
        contextRunner.withPropertyValues("coupang.price-refresh.enabled=true",
            "COUPANG_ACCESS_KEY=test-access", "COUPANG_SECRET_KEY=test-secret",
            "coupang.price-refresh.cron=0 0 4 * * *")
            .run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(CoupangPriceRefreshJob.class);
                var tasks = context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
                assertThat(tasks).hasSize(1);
                assertThat(((CronTask) tasks.iterator().next().getTask()).getExpression()).isEqualTo("0 0 4 * * *");
                verifyNoInteractions(offers);
            });
    }

    @Test
    void 갱신을_켰는데_키가_없으면_잘못된_설정으로_기동을_실패시킨다() {
        contextRunner.withPropertyValues("coupang.price-refresh.enabled=true").run(context ->
            assertThat(context).hasFailed().getFailure().hasRootCauseInstanceOf(IllegalArgumentException.class));
    }
}
