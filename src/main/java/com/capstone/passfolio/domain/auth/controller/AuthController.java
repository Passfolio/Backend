package com.capstone.passfolio.domain.auth.controller;

import com.capstone.passfolio.domain.auth.service.AuthService;
import com.capstone.passfolio.domain.user.dto.UserDto;
import com.capstone.passfolio.system.exception.dto.ErrorResponse;
import com.capstone.passfolio.system.security.jwt.dto.JwtDto;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "인증/인가 API")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "로그아웃 처리합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그아웃 성공", content = @Content(mediaType = "text/plain", examples = @ExampleObject(value = "Logout Successful"))),
            @ApiResponse(responseCode = "401", description = "인증 토큰(JWT)이 누락되었습니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"UNAUTHORIZED\", \"error\": \"JWT_MISSING\", \"message\": \"토큰이 누락되었습니다.\"}")))
    })
    public ResponseEntity<String> logout(HttpServletRequest request, HttpServletResponse response) {
        System.out.println("\n🔥 로그아웃 !\n");
        authService.logout(request, response);
        return ResponseEntity.ok("Logout Successful");
    }

    @DeleteMapping("/delete")
    @Operation(summary = "회원 탈퇴", description = "현재 로그인된 사용자의 계정을 삭제합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "회원 탈퇴 성공", content = @Content(mediaType = "text/plain", examples = @ExampleObject(value = "Soft Delete User Successful"))),
            @ApiResponse(responseCode = "401", description = "인증 토큰이 누락되었습니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"UNAUTHORIZED\", \"error\": \"JWT_MISSING\", \"message\": \"토큰이 누락되었습니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"NOT_FOUND\", \"error\": \"USER_NOT_FOUND\", \"message\": \"존재하지 않는 사용자입니다.\"}")))
    })
    public ResponseEntity<String> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authService.delete(userPrincipal, request, response);
        return ResponseEntity.ok("Soft Delete User Successful");
    }

    // NO AUTH
    @PostMapping("/refresh")
    @Operation(summary = "리프레시 토큰", description = "리프레시 토큰으로 새로운 액세스 토큰과 리프레시 토큰을 발급받습니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "리프레시 토큰 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않거나 만료된 리프레시 토큰입니다.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"status\": \"UNAUTHORIZED\", \"error\": \"JWT_EXPIRED\", \"message\": \"만료된 토큰입니다.\"}")))
    })
    public ResponseEntity<JwtDto.TokenExpiresInfo> refresh(
            @Parameter(description = "로그인 유지(Remember-Me) 여부", example = "false")
            @RequestParam(value = "rememberMe", defaultValue = "false") boolean rememberMe,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return ResponseEntity.ok(authService.refreshTokens(request, response, rememberMe));
    }

    // NO AUTH
    @Hidden
    @PostMapping("/is-blacklisted-rtk")
    public boolean isRtkBlacklisted(@RequestParam("refreshToken") String refreshToken) {
        return authService.isRtkBlacklisted(refreshToken);
    }

    // NO AUTH
    @Hidden
    @PostMapping("/is-blacklisted-atk")
    public boolean isAtkBlacklisted(@RequestParam("accessToken") String accessToken) {
        return authService.isAtkBlacklisted(accessToken);
    }

    @PostMapping("/is-token-active")
    @Operation(summary = "TokenPair 유효성 검증", description = "ATK와 RTK의 유효성을 검증합니다.")
    @ApiResponse(responseCode = "200", description = "TokenPair 유효성 검증")
    public boolean isTokenActive(HttpServletRequest request) {
        return authService.isTokenActive(request);
    }
}

