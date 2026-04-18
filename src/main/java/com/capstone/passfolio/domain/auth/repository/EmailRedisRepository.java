package com.capstone.passfolio.domain.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class EmailRedisRepository {
    private final StringRedisTemplate stringRedisTemplate;

    private static final String CODE_PREFIX = "email:code:";
    private static final String SIGNUP_VERIFIED_PREFIX = "signup:verified:";
    private static final String RESET_PW_VERIFIED_PREFIX = "reset-pw:verified:";

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(15);

    public void saveVerificationCode(String email, String code) {
        stringRedisTemplate.opsForValue().set(CODE_PREFIX + email, code, CODE_TTL);
    }

    public String getVerificationCode(String email) {
        return stringRedisTemplate.opsForValue().get(CODE_PREFIX + email);
    }

    public void deleteVerificationCode(String email) {
        stringRedisTemplate.delete(CODE_PREFIX + email);
    }

    public void markVerifiedForSignup(String email) {
        stringRedisTemplate.opsForValue().set(SIGNUP_VERIFIED_PREFIX + email, "1", VERIFIED_TTL);
    }

    public boolean hasVerifiedForSignup(String email) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(SIGNUP_VERIFIED_PREFIX + email));
    }

    public void clearVerifiedForSignup(String email) {
        stringRedisTemplate.delete(SIGNUP_VERIFIED_PREFIX + email);
    }

    public void markVerifiedForPasswordReset(String email) {
        stringRedisTemplate.opsForValue().set(RESET_PW_VERIFIED_PREFIX + email, "1", VERIFIED_TTL);
    }

    public boolean hasVerifiedForPasswordReset(String email) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(RESET_PW_VERIFIED_PREFIX + email));
    }

    public void clearVerifiedForPasswordReset(String email) {
        stringRedisTemplate.delete(RESET_PW_VERIFIED_PREFIX + email);
    }
}
