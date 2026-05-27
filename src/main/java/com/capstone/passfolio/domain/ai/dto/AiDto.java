package com.capstone.passfolio.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class AiDto {

    // ============================================================
    // PDF 기반 개선 (BE → AI)
    // ============================================================

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiPdfRequest {
        private String pdfS3Url;
        private Long userId;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiCoverLetterRequest {
        private String pdfS3Url;
        @JsonProperty("job_position")
        private String jobPosition;
        private String career;
        private Long userId;
    }

    // AI → BE 즉시 응답 (jobId)
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiJobInitResponse {
        @JsonProperty("job_id")
        private String jobId;
        private String status;
    }

    // AI → BE 작업 상태 조회 응답
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiJobStatusResponse {
        @JsonProperty("job_id")
        private String jobId;
        private String status;
        private AiJobResult result;
        private String message;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiJobResult {
        private String outputPdfS3Url;
    }

    // ============================================================
    // User-facing request DTOs (FE → BE)
    // ============================================================

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "PDF 기반 AI 작업 시작 요청")
    public static class PdfJobRequest {
        @NotNull(message = "fileId는 필수입니다.")
        @Schema(description = "처리할 PDF 파일 ID", example = "1")
        private Long fileId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "자소서/포트폴리오 변환 AI 작업 시작 요청")
    public static class CoverLetterJobRequest {
        @NotNull(message = "fileId는 필수입니다.")
        @Schema(description = "처리할 PDF 파일 ID", example = "1")
        private Long fileId;

        @NotBlank(message = "jobPosition은 필수입니다.")
        @Schema(description = "직무 포지션", example = "백엔드 개발자")
        private String jobPosition;

        @NotBlank(message = "career는 필수입니다.")
        @Schema(description = "경력 구분", example = "신입")
        private String career;
    }

    // ============================================================
    // User-facing response DTOs (BE → FE)
    // ============================================================

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "AI 작업 시작 응답")
    public static class JobInitResponse {
        @Schema(description = "BE 내부 작업 ID (상태 조회 시 사용)", example = "1")
        private Long jobId;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "AI 작업 상태 조회 응답")
    public static class JobStatusResponse {
        @Schema(description = "BE 내부 작업 ID", example = "1")
        private Long jobId;

        @Schema(description = "작업 상태 (PENDING / DONE / ERROR)", example = "DONE")
        private String status;

        @Schema(description = "완료된 output PDF의 S3 URL (DONE 상태일 때만 non-null)", nullable = true)
        private String outputPdfS3Url;

        @Schema(description = "오류 메시지 (ERROR 상태일 때만 non-null)", nullable = true)
        private String errorMessage;
    }
}
