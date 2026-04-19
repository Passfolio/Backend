package com.capstone.passfolio.system.controller;

import com.capstone.passfolio.domain.auth.dto.SystemAuthEmailSendRequest;
import com.capstone.passfolio.domain.auth.dto.SystemAuthEmailVerifyRequest;
import com.capstone.passfolio.domain.auth.dto.SystemAuthLoginRequest;
import com.capstone.passfolio.domain.auth.dto.SystemAuthSignupRequest;
import com.capstone.passfolio.domain.auth.service.SystemAuthService;
import com.capstone.passfolio.system.security.jwt.dto.JwtDto;
import com.capstone.passfolio.system.util.CookieUtils;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// @Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/system/auth")
public class SystemAuthController implements SystemAuthApiSpecification {

    private final SystemAuthService systemAuthService;
    private final CookieUtils cookieUtils;

    @Override
    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody SystemAuthSignupRequest request) {
        systemAuthService.signup(request.email(), request.password(), request.nickname(), request.role());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    @PostMapping("/email/send")
    public ResponseEntity<Void> sendEmailVerification(@Valid @RequestBody SystemAuthEmailSendRequest request) {
        systemAuthService.sendVerificationEmail(request.email(), request.purpose());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/email/verify")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody SystemAuthEmailVerifyRequest request) {
        systemAuthService.verifyEmail(request.email(), request.code(), request.purpose());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<JwtDto.TokenExpiresInfo> login(
            @Valid @RequestBody SystemAuthLoginRequest request, HttpServletResponse response) {
        JwtDto.TokenInfo tokenInfo = systemAuthService.login(request.username(), request.password(), request.rememberMe());
        cookieUtils.addAccessTokenCookie(response, tokenInfo.getAccessToken(), tokenInfo.getRefreshTokenExpiresAt());
        cookieUtils.addRefreshTokenCookie(response, tokenInfo.getRefreshToken(), tokenInfo.getRefreshTokenExpiresAt());
        return ResponseEntity.ok(JwtDto.TokenExpiresInfo.from(tokenInfo));
    }
}
