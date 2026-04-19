package com.capstone.passfolio.domain.auth.controller;

import com.capstone.passfolio.domain.auth.service.AuthService;
import com.capstone.passfolio.system.security.jwt.dto.JwtDto;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthApiSpecification {

    private final AuthService authService;

    @Override
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request, HttpServletResponse response) {
        System.out.println("\n🔥 로그아웃 !\n");
        authService.logout(request, response);
        return ResponseEntity.ok("Logout Successful");
    }

    @Override
    @PostMapping("/revoke-session")
    public ResponseEntity<String> revokeSession(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest request,
            HttpServletResponse response) {
        authService.revokeSession(userPrincipal, request, response);
        return ResponseEntity.ok("Session Revoked");
    }

    @Override
    @DeleteMapping("/delete")
    public ResponseEntity<String> delete(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest request,
            HttpServletResponse response) {
        authService.delete(userPrincipal, request, response);
        return ResponseEntity.ok("Soft Delete User Successful");
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<JwtDto.TokenExpiresInfo> refresh(
            @RequestParam(value = "rememberMe", defaultValue = "false") boolean rememberMe,
            HttpServletRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(authService.refreshTokens(request, response, rememberMe));
    }

    @Override
    @PostMapping("/is-blacklisted-rtk")
    public boolean isRtkBlacklisted(@RequestParam("refreshToken") String refreshToken) {
        return authService.isRtkBlacklisted(refreshToken);
    }

    @Override
    @PostMapping("/is-blacklisted-atk")
    public boolean isAtkBlacklisted(@RequestParam("accessToken") String accessToken) {
        return authService.isAtkBlacklisted(accessToken);
    }

    @Override
    @PostMapping("/is-token-active")
    public boolean isTokenActive(HttpServletRequest request) {
        return authService.isTokenActive(request);
    }
}
