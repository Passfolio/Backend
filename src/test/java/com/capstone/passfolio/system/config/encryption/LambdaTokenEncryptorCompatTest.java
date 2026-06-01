package com.capstone.passfolio.system.config.encryption;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.Test;

/**
 * Lambda(Python crypto.decrypt_github_token)와의 크로스언어 호환 증명용.
 * 고정 키로 토큰을 암호화한 결과(base64)를 build/token-compat.txt에 기록 → 별도 Python 단계가
 * 같은 키로 복호해 일치 검증(스크립트: tools/verify_token_compat.py).
 * 여기서는 Java 측 포맷(iv(12)‖ct+tag, base64)만 자체 검증한다.
 */
class LambdaTokenEncryptorCompatTest {

    // 32바이트 키의 base64 ("01234567890123456789012345678901")
    static final String KEY_B64 = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";
    static final String TOKEN = "ghp_crosslang_TEST_token_0123456789";

    @Test
    void emit_ciphertext_for_python_and_verify_format() throws Exception {
        LambdaTokenEncryptor enc = new LambdaTokenEncryptor(KEY_B64);
        String cipher = enc.encrypt(TOKEN);

        // 포맷 자체 검증: base64 디코드 길이 ≥ 12(iv)+16(tag).
        byte[] raw = Base64.getDecoder().decode(cipher);
        assertThat(raw.length).isGreaterThanOrEqualTo(12 + 16);

        // Python 복호 단계로 넘길 산출물 기록(key\n token\n cipher).
        Path out = Path.of("build", "token-compat.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, KEY_B64 + "\n" + TOKEN + "\n" + cipher + "\n");
    }
}
