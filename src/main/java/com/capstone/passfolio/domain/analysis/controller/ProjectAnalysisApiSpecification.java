package com.capstone.passfolio.domain.analysis.controller;

import com.capstone.passfolio.domain.analysis.dto.ProjectAnalysisDto;
import com.capstone.passfolio.system.exception.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "Project Analysis", description = "프로젝트 분석 — Lambda 연동 내부 API")
public interface ProjectAnalysisApiSpecification {

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
