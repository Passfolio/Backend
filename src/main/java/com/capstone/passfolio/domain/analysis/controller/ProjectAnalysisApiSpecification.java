package com.capstone.passfolio.domain.analysis.controller;

import com.capstone.passfolio.domain.analysis.dto.ProjectAnalysisDto;
import com.capstone.passfolio.system.exception.dto.ErrorResponse;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Project Analysis", description = "프로젝트 분석 — 디스패치 + Lambda 연동")
public interface ProjectAnalysisApiSpecification {

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "분석 완료 SSE 구독",
            description = "개별 repo 완료(PROJECT_ANALYSIS_STATUS)·배치 전체 완료"
                    + "(PROJECT_ANALYSIS_BATCH_STATUS) 이벤트를 실시간 수신하는 SSE 연결.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE 연결 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    SseEmitter subscribeToAnalysis(UserPrincipal userPrincipal);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "프로젝트 분석 시작",
            description = "사용자의 GitHub 저장소 분석을 시작한다. 토큰 검증·크기 게이트·admission 후 "
                    + "Lambda(SQS)로 디스패치하고 IN_PROGRESS를 반환한다. 완료는 webhook으로 갱신된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "디스패치 성공(IN_PROGRESS)"),
            @ApiResponse(responseCode = "401", description = "인증 필요 / GitHub 토큰 만료·부족",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "저장소 크기 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "디스패치 rate 초과 — 재시도",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "디스패치 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ProjectAnalysisDto.StartResponse> startAnalysis(
            UserPrincipal userPrincipal,
            @Valid ProjectAnalysisDto.StartRequest request);

    @Operation(
            summary = "분석 완료 콜백 (Lambda 전용)",
            description = "Lambda가 분석 완료/실패 시 호출하는 내부 Webhook. X-INTERNAL-API-KEY 필요.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공 (멱등성 보장 — 이미 종료된 분석도 200 반환)"),
            @ApiResponse(responseCode = "401", description = "내부 API 키 누락/불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "분석을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> completeAnalysis(@Valid ProjectAnalysisDto.WebhookCompleteRequest request);
}
