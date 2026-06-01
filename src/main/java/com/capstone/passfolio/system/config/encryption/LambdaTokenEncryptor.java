package com.capstone.passfolio.system.config.encryption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;

import java.util.Base64;
import java.util.Map;

/**
 * Lambda 전달용 GitHub 토큰 암호화 — <b>KMS Encrypt</b>(CMK alias/passfolio/github-token-payload).
 * Redis 저장용 {@link AesEncryptor}(원시 키)와 별개의 추가 키이며, 키 자체는 KMS가 관리한다
 * (원시 키 공유 없음). EncryptionContext purpose=clone_and_author_history로 용도를 바인딩 —
 * EC2 role의 kms:Encrypt 정책 조건 및 Lambda kms:Decrypt와 동일해야 한다.
 * Lambda crypto.decrypt_github_token(boto3 kms.decrypt, 동일 context)이 복호한다.
 */
@Component
public class LambdaTokenEncryptor {

    /** EC2 role 정책 조건 / Lambda decrypt와 반드시 일치. */
    static final Map<String, String> ENCRYPTION_CONTEXT = Map.of("purpose", "clone_and_author_history");

    private final KmsClient kmsClient;
    private final String keyId;

    public LambdaTokenEncryptor(KmsClient kmsClient, @Value("${aws.kms.token-key-id}") String keyId) {
        this.kmsClient = kmsClient;
        this.keyId = keyId;
    }

    /** 평문 토큰 → KMS Encrypt(+context) → base64(ciphertext blob). */
    public String encrypt(String plaintext) {
        EncryptResponse response = kmsClient.encrypt(EncryptRequest.builder()
                .keyId(keyId)
                .encryptionContext(ENCRYPTION_CONTEXT)
                .plaintext(SdkBytes.fromUtf8String(plaintext))
                .build());
        return Base64.getEncoder().encodeToString(response.ciphertextBlob().asByteArray());
    }
}
