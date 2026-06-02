package com.capstone.passfolio.domain.user.controller;

import com.capstone.passfolio.common.dto.PageDto;
import com.capstone.passfolio.domain.user.dto.UserDto;
import com.capstone.passfolio.domain.user.service.UserService;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ADMIN 전용 회원 관리 컨트롤러.
 *
 * <p>{@code /api/v1/admin/**} 이라 SecurityConfig 기본 규칙으로 JWT 인증이 요구되고,
 * ADMIN 역할 검증은 서비스 레이어({@code assertAdminCaller})가 수행한다(프로젝트 컨벤션).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin User", description = "ADMIN 전용 회원 관리(유입 집계·목록)")
public class AdminUserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;

    @GetMapping("/signups-daily")
    @Operation(summary = "날짜별 가입자 수(ADMIN)",
            description = "createdAt 기준 일별 신규 가입자 수를 오름차순 반환. 사용자 유입 그래프용. ADMIN 전용.")
    public ResponseEntity<List<UserDto.DailySignupResponse>> dailySignups(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(userService.getDailySignups(userPrincipal));
    }

    @GetMapping
    @Operation(summary = "회원 목록 조회(ADMIN)",
            description = "회원을 페이지 단위로 조회(우선 userId·nickname). ADMIN 전용.")
    public ResponseEntity<PageDto.PageListResponse<UserDto.UserSummaryResponse>> list(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "id"));
        return ResponseEntity.ok(userService.listUsers(userPrincipal, pageable));
    }
}
