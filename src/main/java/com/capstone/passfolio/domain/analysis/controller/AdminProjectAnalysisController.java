package com.capstone.passfolio.domain.analysis.controller;

import com.capstone.passfolio.domain.analysis.dto.ProjectAnalysisDto;
import com.capstone.passfolio.domain.analysis.service.ProjectAnalysisService;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN 전용 분석 부하/메트릭 테스트 컨트롤러.
 *
 * <p>일반 사용자 흐름(/api/v1/project-analysis/start)과 분리된 엔드포인트로, 공개 repo 다수를
 * 토큰 없이 동시 디스패치한다(파이프라인 거동·AOP·동시성·레이트리밋 관측용). 경로는
 * {@code /api/v1/admin/**} 이라 SecurityConfig의 기본 규칙으로 JWT 인증이 요구되고, ADMIN
 * 역할 검증은 서비스 레이어({@code assertAdmin})가 수행한다(프로젝트 컨벤션).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/project-analysis")
@Tag(name = "Admin Project Analysis", description = "ADMIN 전용 분석 부하 테스트")
public class AdminProjectAnalysisController {

    private final ProjectAnalysisService projectAnalysisService;

    @PostMapping("/test-batch")
    @Operation(summary = "공개 repo 다수 동시 디스패치(ADMIN)",
            description = "토큰 없이 공개 repo를 dominant 기여자 모드·mode=STEP로 디스패치. ADMIN 전용.")
    public ResponseEntity<ProjectAnalysisDto.AdminTestBatchResponse> testBatch(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody ProjectAnalysisDto.AdminTestBatchRequest request) {
        return ResponseEntity.ok(projectAnalysisService.initiateAdminTestBatch(userPrincipal, request));
    }
}
