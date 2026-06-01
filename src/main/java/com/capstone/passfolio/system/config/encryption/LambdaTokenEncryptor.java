package com.capstone.passfolio.system.config.encryption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Lambda 전달용 GitHub 토큰 암호화기. 기존 {@link AesEncryptor}(Redis 저장용)와 동일한
 * AES-256-GCM 포맷(12B nonce ‖ ct+tag, base64)이되 <b>다른 추가 키</b>(GITHUB_AES_KEY)를 쓴다.
 * 이 키는 Lambda와 공유(운영: Secrets Manager/KMS, dev: 환경변수)되며, Lambda의
 * crypto.decrypt_github_token(동일 포맷)이 복호화한다.
 */
@Component
public class LambdaTokenEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKeySpec secretKey;

    public LambdaTokenEncryptor(@Value("${analysis.dispatch.github-aes-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("GITHUB_AES_KEY must decode to 32 bytes (256 bits)");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /** 평문 → base64(iv(12) ‖ ciphertext+tag). Lambda crypto.decrypt_github_token과 호환. */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes());

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Lambda token encryption failed", e);
        }
    }
}
