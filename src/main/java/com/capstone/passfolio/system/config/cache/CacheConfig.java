package com.capstone.passfolio.system.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("userPrincipal");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000) // t3.small 메모리를 고려하여 최대 1000개까지만 보관
                .expireAfterWrite(10, TimeUnit.MINUTES) // 인증 정보이므로 10분 후 만료 (보안 정합성 유지)
                .recordStats());
        return cacheManager;
    }
}