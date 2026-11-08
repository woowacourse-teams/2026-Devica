package com.wrb.devica.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.data.domain.Slice;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
@Configuration
public class CacheConfig {

    public static final String PRODUCT_LIST = "productList";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(PRODUCT_LIST);

        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumWeight(20_000)
            .weigher((key, value) -> Math.max(1, ((Slice<?>) value).getNumberOfElements()))
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats());

        return new TransactionAwareCacheManagerProxy(cacheManager);
    }
}
