package com.capstone.passfolio.domain.analysis.service;

import com.capstone.passfolio.system.config.encryption.AesEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 배치 완료 SMS용 전화번호를 transient 보관 — DB에 저장하지 않고(OAuth는 phone 미제공),
 * 요청 시 받은 phone을 AES 암호화해 Redis(batchId 키, 배치 TTL)에 둔다. all-done 시 복호해 발송.
 * phone은 Lambda로 전달되지 않고 Spring 도메인 안에서만 다뤄진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchPhoneStore {

    private final StringRedisTemplate stringRedisTemplate;
    private final AesEncryptor aesEncryptor; // Redis 저장용 AES(기존 토큰 암호화기 재사용)

    @Value("${analysis.dispatch.batch-ttl-hours:6}")
    private long batchTtlHours;

    private String key(String batchId) { return "analysis:batch:" + batchId + ":phone"; }

    /** phone을 암호화해 저장(없으면 no-op). */
    public void store(String batchId, String phone) {
        if (phone == null || phone.isBlank()) return;
        stringRedisTemplate.opsForValue().set(
                key(batchId), aesEncryptor.encrypt(phone.trim()), Duration.ofHours(batchTtlHours));
    }

    /** 복호한 phone(없거나 복호 실패 시 null). */
    public String read(String batchId) {
        String enc = stringRedisTemplate.opsForValue().get(key(batchId));
        if (enc == null) return null;
        try {
            return aesEncryptor.decrypt(enc);
        } catch (Exception e) {
            log.warn("[BatchPhoneStore] phone 복호 실패. batchId={}", batchId, e);
            return null;
        }
    }
}
