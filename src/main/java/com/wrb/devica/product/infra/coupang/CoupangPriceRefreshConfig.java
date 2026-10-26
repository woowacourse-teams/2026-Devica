package com.wrb.devica.product.infra.coupang;

import com.wrb.devica.product.service.ProductOfferService;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "coupang.price-refresh", name = "enabled", havingValue = "true")
@EnableScheduling
public class CoupangPriceRefreshConfig {

    @Bean(destroyMethod = "close")
    HttpClient coupangHttpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    @Bean
    CoupangProductSearchClient coupangProductSearchClient(
        HttpClient coupangHttpClient,
        @Value("${COUPANG_ACCESS_KEY:}") String accessKey,
        @Value("${COUPANG_SECRET_KEY:}") String secretKey
    ) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(coupangHttpClient);
        requests.setReadTimeout(Duration.ofSeconds(10));
        return new CoupangProductSearchClient(RestClient.builder().requestFactory(requests).build(),
            new CoupangHmacSigner(accessKey, secretKey, Clock.systemUTC()));
    }

    @Bean
    CoupangPriceRefreshJob coupangPriceRefreshJob(CoupangProductSearchClient client, ProductOfferService offers,
        @Value("${app.public-base-url:https://devica.co.kr}") String publicBaseUrl) {
        return new CoupangPriceRefreshJob(client, offers, Clock.system(ZoneId.of("Asia/Seoul")), publicBaseUrl);
    }
}
