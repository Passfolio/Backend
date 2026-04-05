package com.capstone.passfolio.system.security.jwt.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class TokenRedisRepository {
    private final StringRedisTemplate stringRedisTemplate;

    // Whitelist
    private String keyRtkAllow(String subject) { return "rt:allow:" + subject; }
    private String keyRtkMeta(String uuid) { return "rt:meta:" + uuid; }

    // Blacklist
    private String keyRtkBlack(String uuid) { return "rt:black:" + uuid; }
    private String keyAtkBlack(String jti) { return "at:black:" + jti; }

    // Retry Counter (Race Condition 패배자 재시도 추적)
    private String keyRtkRetry(String uuid) { return "rt:retry:" + uuid; }

    public void allowRtk(String subject, String uuid, Duration ttl) {
        stringRedisTemplate.opsForValue().set(keyRtkAllow(subject), uuid, ttl);
        stringRedisTemplate.opsForValue().set(keyRtkMeta(uuid), subject, ttl);
    }

    public String getAllowedRtk(String subject) {
        return stringRedisTemplate.opsForValue().get(keyRtkAllow(subject));
    }

    public void setBlacklistRtk(String uuid, Duration ttl) {
        stringRedisTemplate.opsForValue().set(keyRtkBlack(uuid), "1", ttl);
    }

    public boolean isRtkBlacklisted(String uuid) {
        String v = stringRedisTemplate.opsForValue().get(keyRtkBlack(uuid));
        return v != null;
    }

    public void clearAllowedRtk(String subject) {
        stringRedisTemplate.delete(keyRtkAllow(subject));
    }

    public void setBlacklistAtkJti(String jti, Duration ttl) {
        stringRedisTemplate.opsForValue().set(keyAtkBlack(jti), "1", ttl);
    }

    public boolean isAtkBlacklisted(String jti) {
        String v = stringRedisTemplate.opsForValue().get(keyAtkBlack(jti));
        return v != null;
    }

    /**
     * 블랙리스트에 등록된 RTK의 남은 TTL 조회
     * @param uuid RTK UUID
     * @return 남은 TTL (없으면 Duration.ZERO)
     */
    public Duration getBlacklistRtkTtl(String uuid) {
        Long ttlSeconds = stringRedisTemplate.getExpire(keyRtkBlack(uuid));
        if (ttlSeconds == null || ttlSeconds < 0) {
            return Duration.ZERO;
        }
        return Duration.ofSeconds(ttlSeconds);
    }

    /**
     * old RTK 재시도 카운터 증가 (Race Condition 패배자 추적)
     * @param uuid old RTK UUID
     * @param gracePeriod Grace Period (보통 30초)
     * @return 증가된 카운터 값
     */
    public Long incrementRtkRetryCount(String uuid, Duration gracePeriod) {
        String key = keyRtkRetry(uuid);
        Long count = stringRedisTemplate.opsForValue().increment(key);

        // 첫 증가 시 TTL 설정 (Grace Period 동안만 유지)
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, gracePeriod);
        }

        return count != null ? count : 0L;
    }

    /**
     * old RTK 재시도 카운터 조회
     * @param uuid old RTK UUID
     * @return 카운터 값 (없으면 0)
     */
    public Long getRtkRetryCount(String uuid) {
        String value = stringRedisTemplate.opsForValue().get(keyRtkRetry(uuid));
        return value != null ? Long.parseLong(value) : 0L;
    }
}
