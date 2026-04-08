package com.capstone.passfolio.domain.github.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class GitHubTokenRedisRepository {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${github.token.ttl-hours:8}")
    private int tokenTtlHours;

    @Value("${github.cache.repos-ttl-minutes:5}")
    private int reposCacheTtlMinutes;

    // --- Access Token ---

    private String keyToken(Long userId) { return "github:token:" + userId; }

    public void saveAccessToken(Long userId, String encryptedToken) {
        stringRedisTemplate.opsForValue().set(
                keyToken(userId), encryptedToken, Duration.ofHours(tokenTtlHours)
        );
    }

    public Optional<String> getAccessToken(Long userId) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(keyToken(userId)));
    }

    public void deleteAccessToken(Long userId) {
        stringRedisTemplate.delete(keyToken(userId));
    }

    // --- Repos Cache ---

    private String keyRepos(Long userId, String type, int page) {
        return "github:repos:" + userId + ":" + type + ":" + page;
    }

    public void cacheRepos(Long userId, String type, int page, String jsonData) {
        stringRedisTemplate.opsForValue().set(
                keyRepos(userId, type, page), jsonData, Duration.ofMinutes(reposCacheTtlMinutes)
        );
    }

    public Optional<String> getCachedRepos(Long userId, String type, int page) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(keyRepos(userId, type, page)));
    }

    // --- Profile Cache ---

    private String keyProfile(Long userId) { return "github:profile:" + userId; }

    public void cacheProfile(Long userId, String jsonData) {
        stringRedisTemplate.opsForValue().set(keyProfile(userId), jsonData, Duration.ofMinutes(10));
    }

    public Optional<String> getCachedProfile(Long userId) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(keyProfile(userId)));
    }

    // --- 캐시 무효화 ---

    public void invalidateProfileCache(Long userId) {
        stringRedisTemplate.delete(keyProfile(userId));
    }
}
