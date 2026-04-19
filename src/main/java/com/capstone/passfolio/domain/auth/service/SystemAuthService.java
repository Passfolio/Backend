package com.capstone.passfolio.domain.auth.service;

import com.capstone.passfolio.domain.auth.dto.EmailPurpose;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.entity.enums.Role;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import com.capstone.passfolio.system.security.jwt.dto.JwtDto;
import com.capstone.passfolio.system.security.jwt.service.TokenService;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemAuthService {

    private static final String DEFAULT_PROFILE_IMAGE_URL = "https://avatars.githubusercontent.com/u/0?v=4";

    @Value("${app.system-auth.enabled:false}")
    private boolean systemAuthEnabled;

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final SystemAuthEmailAllowlist systemAuthEmailAllowlist;

    /** 회원가입용 이메일 인증 코드 발송 — `purpose`는 `SIGNUP`만 허용 */
    public void sendVerificationEmail(String email, EmailPurpose purpose) {
        ensureEnabled();
        String norm = normalize(email);
        systemAuthEmailAllowlist.requireAllowed(norm);
        requireSignupPurpose(purpose);
        if (userRepository.findByUsername(norm).isPresent()) {
            throw new RestException(ErrorCode.USER_USERNAME_ALREADY_EXISTS);
        }
        emailService.sendEmail(norm);
    }

    /** 회원가입용 이메일 검증 — `purpose`는 `SIGNUP`만 허용 */
    public void verifyEmail(String email, String code, EmailPurpose purpose) {
        ensureEnabled();
        String norm = normalize(email);
        systemAuthEmailAllowlist.requireAllowed(norm);
        requireSignupPurpose(purpose);
        if (userRepository.findByUsername(norm).isPresent()) {
            throw new RestException(ErrorCode.USER_USERNAME_ALREADY_EXISTS);
        }
        emailService.verifyEmailCode(norm, code, EmailPurpose.SIGNUP);
    }

    @Transactional
    public void signup(String email, String rawPassword, String nickname, Role role) {
        ensureEnabled();
        String norm = normalize(email);
        systemAuthEmailAllowlist.requireAllowed(norm);
        if (!emailService.hasVerifiedForSignup(norm)) {
            throw new RestException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }
        if (userRepository.findByUsername(norm).isPresent()) {
            throw new RestException(ErrorCode.USER_USERNAME_ALREADY_EXISTS);
        }
        User user =
                User.builder()
                        .username(norm)
                        .nickname(resolveNickname(norm, nickname))
                        .profileImageUrl(DEFAULT_PROFILE_IMAGE_URL)
                        .role(role)
                        .githubId(null)
                        .githubLogin(null)
                        .password(passwordEncoder.encode(rawPassword))
                        .build();
        userRepository.save(user);
        emailService.clearVerifiedForSignup(norm);
    }

    /** 시스템 로그인 */
    @Transactional
    public JwtDto.TokenInfo login(String username, String password, boolean rememberMe) {
        ensureEnabled();
        User user = findSystemUserOrThrow(normalize(username));
        if (!user.isSystemLoginUser()) {
            throw new RestException(ErrorCode.AUTH_SYSTEM_USER_REQUIRED);
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RestException(ErrorCode.AUTH_PASSWORD_NOT_MATCH);
        }

        UserPrincipal principal = UserPrincipal.from(user);
        return tokenService.issueTokens(JwtDto.TokenOptionWrapper.of(principal, rememberMe));
    }

    private void requireSignupPurpose(EmailPurpose purpose) {
        if (purpose == null || purpose != EmailPurpose.SIGNUP) {
            throw new RestException(ErrorCode.GLOBAL_BAD_REQUEST);
        }
    }

    private void ensureEnabled() {
        if (!systemAuthEnabled) {
            throw new RestException(ErrorCode.SYSTEM_AUTH_DISABLED);
        }
    }

    private User findSystemUserOrThrow(String normalizedUsername) {
        return userRepository
                .findByUsername(normalizedUsername)
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));
    }

    private static String resolveNickname(String normalizedEmail, String nickname) {
        if (nickname != null && !nickname.isBlank()) {
            return nickname.trim();
        }
        int at = normalizedEmail.indexOf('@');
        return at > 0 ? normalizedEmail.substring(0, at) : "user";
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }
}
