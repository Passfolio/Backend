package com.capstone.passfolio.domain.ai.controller;

import com.capstone.passfolio.domain.ai.dto.AiDto;
import com.capstone.passfolio.system.exception.dto.ErrorResponse;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "AI", description = "AI 서버 연동 API — PDF 기반 문서 처리")
public interface AiApiSpecification {

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "포트폴리오 PDF 개선 작업 시작",
            description = "업로드된 포트폴리오 PDF를 AI가 개선한다. 작업 ID를 즉시 반환하며 비동기로 처리된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "작업 시작 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "AI 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AiDto.JobInitResponse> startPortfolioFromPdf(
            UserPrincipal userPrincipal,
            @Valid AiDto.PdfJobRequest request);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "자소서 PDF 개선 작업 시작",
            description = "자소서 PDF를 AI가 개선한다. 비동기 처리.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "작업 시작 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "AI 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AiDto.JobInitResponse> startCoverLetterFromPdf(
            UserPrincipal userPrincipal,
            @Valid AiDto.PdfJobRequest request);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "포트폴리오 PDF → 자소서 생성 작업 시작",
            description = "포트폴리오 PDF를 기반으로 자소서를 생성한다. 비동기 처리.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "작업 시작 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "AI 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AiDto.JobInitResponse> startCoverLetterFromPortfolio(
            UserPrincipal userPrincipal,
            @Valid AiDto.CoverLetterJobRequest request);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "자소서 PDF → 포트폴리오 생성 작업 시작",
            description = "자소서 PDF를 기반으로 포트폴리오를 생성한다. 비동기 처리.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "작업 시작 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "AI 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AiDto.JobInitResponse> startPortfolioFromCoverLetter(
            UserPrincipal userPrincipal,
            @Valid AiDto.CoverLetterJobRequest request);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "코드 분석 URL 기반 로드맵 평가 작업 시작",
            description = "완료된 코드 분석(analysisIds)의 CDN URL을 FastAPI에 전달해 로드맵 커버리지 진단 + LLM 평가를 시작한다. 비동기 처리.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "작업 시작 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "AI 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AiDto.JobInitResponse> startRoadmapAssess(
            UserPrincipal userPrincipal,
            @Valid AiDto.RoadmapJobRequest request);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "AI 작업 상태 조회",
            description = "비동기 AI 작업의 현재 상태를 조회한다. 상태는 AI 서버 Webhook(POST /jobs/complete)으로 업데이트된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상태 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "작업을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AiDto.JobStatusResponse> getJobStatus(
            UserPrincipal userPrincipal,
            @Parameter(description = "BE 작업 ID", required = true) Long jobId);

    @Operation(
            summary = "AI 작업 완료 콜백 (AI 서버 전용)",
            description = "AI 서버가 작업 완료/실패 시 호출하는 내부 Webhook. 인증 불필요.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공 (멱등성 보장 — 이미 완료된 잡도 200 반환)"),
            @ApiResponse(responseCode = "404", description = "작업을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> completeJob(@Valid AiDto.JobCompleteRequest request);

    @Operation(
            summary = "로드맵 평가 완료 콜백 (AI 서버 전용)",
            description = "로드맵 평가 완료/실패 시 AI 서버가 호출하는 내부 Webhook. 인증 불필요.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공 (멱등성 보장)"),
            @ApiResponse(responseCode = "404", description = "작업을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> completeRoadmapJob(@Valid AiDto.RoadmapCompleteRequest request);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "AI 작업 완료 SSE 구독",
            description = "AI 작업 완료 시 실시간 이벤트를 수신하기 위한 SSE 연결. 이벤트명: AI_JOB_STATUS.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE 연결 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    SseEmitter subscribeToJobStatus(UserPrincipal userPrincipal);
}
