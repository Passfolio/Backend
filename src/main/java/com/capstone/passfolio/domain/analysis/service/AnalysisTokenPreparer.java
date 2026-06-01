package com.capstone.passfolio.domain.analysis.service;

import com.capstone.passfolio.domain.github.repository.GitHubTokenRedisRepository;
import com.capstone.passfolio.system.config.encryption.AesEncryptor;
import com.capstone.passfolio.system.config.encryption.LambdaTokenEncryptor;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 디스패치용 GitHub 토큰 준비:
 *  1) Redis 조회 → AESGCM 복호 (기존 저장 키)
 *  2) Redis TTL 잔여가 임계값 이상인지 점검(분석 소요+큐 대기 여유)
 *  3) (전송 직전) TTL 재점검
 *  4) KMS CMK로 재암호화(기존 AES와 다른 추가 키, Lambda와 공유)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisTokenPreparer {

    private final GitHubTokenRedisRepository tokenRedisRepository;
    private final AesEncryptor aesEncryptor;
    private final LambdaTokenEncryptor lambdaTokenEncryptor;

    @Value("${analysis.dispatch.min-token-ttl-minutes}")
    private long minTokenTtlMinutes;

    /** TTL 점검 후 토큰 조회+AESGCM 복호 → 평문. */
    public String resolvePlaintextWithTtlCheck(Long userId) {
        assertSufficientTtl(userId);
        String encrypted = tokenRedisRepository.getAccessToken(userId)
                .orElseThrow(() -> new RestException(ErrorCode.GITHUB_TOKEN_NOT_FOUND));
        try {
            return aesEncryptor.decrypt(encrypted);
        } catch (Exception e) {
            log.error("[AnalysisTokenPreparer] GitHub 토큰 복호 실패. userId={}", userId, e);
            tokenRedisRepository.deleteAccessToken(userId);
            throw new RestException(ErrorCode.GITHUB_TOKEN_NOT_FOUND);
        }
    }

    /** 큐/페이서 대기 후 전송 직전 TTL 재점검. 부족 시 ANALYSIS_TOKEN_EXPIRING_SOON. */
    public void assertSufficientTtl(Long userId) {
        Long ttlSeconds = tokenRedisRepository.getAccessTokenTtlSeconds(userId);
        if (ttlSeconds == null || ttlSeconds < minTokenTtlMinutes * 60) {
            throw new RestException(ErrorCode.ANALYSIS_TOKEN_EXPIRING_SOON);
        }
    }

    /** Lambda 전달용 재암호화(공유 AES 키, Lambda crypto와 동일 포맷) → base64. */
    public String reencryptForLambda(String plaintextToken) {
        return lambdaTokenEncryptor.encrypt(plaintextToken);
    }
}
