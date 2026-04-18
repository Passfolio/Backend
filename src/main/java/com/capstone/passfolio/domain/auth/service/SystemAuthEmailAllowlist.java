package com.capstone.passfolio.domain.auth.service;

import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code SYSTEM_AUTH_EMAIL_ALLOWLIST}(.env) — 쉼표로 구분된 이메일만 시스템 가입·메일 발송·검증 허용.
 * 목록이 비어 있으면 허용 주소 없음(모두 거절).
 */
@Component
public class SystemAuthEmailAllowlist {

    private final Set<String> allowedNormalizedEmails;

    public SystemAuthEmailAllowlist(
            @Value("${app.system-auth.email-allowlist:}") String rawList) {
        this.allowedNormalizedEmails = parse(rawList);
    }

    private static Set<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public void requireAllowed(String normalizedEmail) {
        String n = normalizedEmail == null ? "" : normalizedEmail.trim().toLowerCase(Locale.ROOT);
        if (allowedNormalizedEmails.isEmpty() || !allowedNormalizedEmails.contains(n)) {
            throw new RestException(ErrorCode.AUTH_EMAIL_NOT_ALLOWLISTED);
        }
    }
}
