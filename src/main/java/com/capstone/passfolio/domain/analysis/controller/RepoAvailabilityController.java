package com.capstone.passfolio.domain.analysis.controller;

import com.capstone.passfolio.domain.analysis.dto.RepoAvailabilityDto;
import com.capstone.passfolio.domain.analysis.service.RepoAvailabilityService;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * repo 사전 점검(Precheck) API. 분석 시작 전 repo의 분석 가능성을 미리 점검한다.
 * - 사용자 엔드포인트(POST/GET): /api/v1/** 기본 JWT 인증.
 * - 웹훅: Lambda 내부 호출 — InternalApiKeyFilter(X-INTERNAL-API-KEY)로 보호(SecurityConfig 등록).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/project-analysis/precheck")
@Tag(name = "Project Analysis Precheck", description = "repo 사전 분석 가능성 점검")
public class RepoAvailabilityController {

    private final RepoAvailabilityService repoAvailabilityService;

    @PostMapping
    @Operation(summary = "repo 사전 점검 시작", description = "선택한 repo(≤5)를 병렬 점검 디스패치. 결과는 SSE로 통지.")
    public ResponseEntity<RepoAvailabilityDto.PrecheckStartResponse> startPrecheck(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody RepoAvailabilityDto.PrecheckStartRequest request) {
        return ResponseEntity.ok(
                repoAvailabilityService.initiatePrecheck(userPrincipal.getUserId(), request.getRepoUrls()));
    }

    @GetMapping
    @Operation(summary = "내 repo 점검 상태 목록", description = "현재 사용자의 repo별 점검 상태(FE 리스트/필터용).")
    public ResponseEntity<List<RepoAvailabilityDto.PrecheckItem>> listPrecheck(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(repoAvailabilityService.listAvailability(userPrincipal.getUserId()));
    }

    @PostMapping("/webhook")
    @Operation(summary = "사전 점검 결과 콜백(내부)", description = "Lambda → BE. X-INTERNAL-API-KEY로 인증.")
    public ResponseEntity<Void> precheckWebhook(
            @Valid @RequestBody RepoAvailabilityDto.PrecheckWebhookRequest request) {
        repoAvailabilityService.applyPrecheckResult(request);
        return ResponseEntity.ok().build();
    }
}
