package com.capstone.passfolio.domain.analysis.controller;

import com.capstone.passfolio.domain.analysis.dto.ProjectAnalysisDto;
import com.capstone.passfolio.domain.analysis.service.ProjectAnalysisSseService;
import com.capstone.passfolio.domain.analysis.service.ProjectAnalysisService;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/project-analysis")
public class ProjectAnalysisController implements ProjectAnalysisApiSpecification {

    private final ProjectAnalysisService projectAnalysisService;
    private final ProjectAnalysisSseService projectAnalysisSseService;

    @Override
    @GetMapping("/subscribe")
    public SseEmitter subscribeToAnalysis(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return projectAnalysisSseService.subscribe(userPrincipal.getUserId());
    }

    // FE 분석 시작(JWT 인증, 다중 repo ≤3). /api/v1/** 기본 authenticated 규칙으로 보호됨.
    @Override
    @PostMapping("/start")
    public ResponseEntity<ProjectAnalysisDto.StartResponse> startAnalysis(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody ProjectAnalysisDto.StartRequest request) {
        return ResponseEntity.ok(projectAnalysisService.initiateBatch(userPrincipal.getUserId(), request));
    }

    // Lambda 완료 콜백. 인증은 InternalApiKeyFilter(X-INTERNAL-API-KEY)가 필터 단계에서 처리.
    @Override
    @PostMapping("/webhook")
    public ResponseEntity<Void> completeAnalysis(@Valid @RequestBody ProjectAnalysisDto.WebhookCompleteRequest request) {
        projectAnalysisService.completeAnalysis(request);
        return ResponseEntity.ok().build();
    }

    // 진행중 페이지: 본인 소유 배치의 repo별 상태(폴링 백업 + 새로고침 초기로드). 라이브는 SSE.
    @GetMapping("/batch/{batchId}")
    public ResponseEntity<ProjectAnalysisDto.AdminBatchStatusResponse> userBatchStatus(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String batchId) {
        return ResponseEntity.ok(projectAnalysisService.getUserBatchStatus(userPrincipal.getUserId(), batchId));
    }
}
