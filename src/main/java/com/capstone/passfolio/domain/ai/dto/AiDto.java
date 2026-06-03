package com.capstone.passfolio.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
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
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL) // null 필드 생략 → FastAPI Pydantic 기본값(code_analysis_urls=[]) 적용(명시적 null은 422)
    public static class AiPdfRequest {
        private String pdfUrl;
        private Long userId;
        // @JsonNaming(SnakeCase) → JSON 키 code_analysis_urls(복수, FastAPI 계약과 일치). NONSTOP: 없으면 rag만.
        private java.util.List<String> codeAnalysisUrls;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL) // null 필드 생략 → FastAPI Pydantic 기본값 적용(명시적 null은 422)
    public static class AiCoverLetterRequest {
        private String pdfUrl;
        @JsonProperty("job_position")
        private String jobPosition;
        private String career;
        private Long userId;
        // @JsonNaming(SnakeCase) → JSON 키 code_analysis_urls(복수, FastAPI 계약과 일치). NONSTOP: 없으면 rag만.
        private java.util.List<String> codeAnalysisUrls;
    }

    // 로드맵 평가 요청 (BE → FastAPI)
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AiRoadmapRequest {
        private String aiJobId;           // JSON: ai_job_id — BE가 생성한 UUID, FastAPI가 콜백에 그대로 사용
        private List<String> codeAnalysisUrls;
        private boolean merge;
    }

    // AI → BE 즉시 응답 (jobId)
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class AiJobResult {
        private String outputPdfUrl;
    }

    // ============================================================
    // AI → BE Webhook (AI 서버 완료 콜백)
    // ============================================================

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "AI 서버 작업 완료 콜백 요청")
    public static class JobCompleteRequest {
        @NotBlank(message = "aiJobId는 필수입니다.")
        @Schema(description = "AI 서버가 발급한 작업 ID", example = "abc-123")
        private String aiJobId;

        @NotBlank(message = "status는 필수입니다.")
        @Pattern(
                regexp = "^(DONE|ERROR)$",
                message = "status는 DONE 또는 ERROR만 허용됩니다."
        )
        @Schema(description = "작업 결과 상태 (DONE 또는 ERROR)", example = "DONE")
        private String status;

        @URL(message = "outputPdfUrl이 유효한 URL 형식이 아닙니다.")
        @Schema(description = "완료된 output PDF URL (DONE 상태일 때)", nullable = true)
        private String outputPdfUrl;

        @Schema(description = "오류 메시지 (ERROR 상태일 때)", nullable = true)
        private String errorMessage;
    }

    // 로드맵 평가 완료 콜백 (POST /api/v1/ai/roadmap/complete — AI 서버 전용)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "로드맵 평가 완료 콜백 요청 (AI 서버 전용)")
    public static class RoadmapCompleteRequest {
        @NotBlank(message = "aiJobId는 필수입니다.")
        @Schema(description = "BE가 /roadmap/assess 요청 시 전달한 ai_job_id", example = "uuid-123")
        private String aiJobId;

        @NotBlank(message = "status는 필수입니다.")
        @Pattern(regexp = "^(DONE|ERROR)$", message = "status는 DONE 또는 ERROR만 허용됩니다.")
        @Schema(description = "작업 결과 상태 (DONE 또는 ERROR)", example = "DONE")
        private String status;

        @Schema(description = "로드맵 평가 결과 JSON 배열 (DONE 상태일 때)", nullable = true)
        private JsonNode result;

        @Schema(description = "오류 메시지 (ERROR 상태일 때)", nullable = true)
        private String errorMessage;
    }

    // ============================================================
    // User-facing request DTOs (FE → BE)
    // ============================================================

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "로드맵 평가 AI 작업 시작 요청")
    public static class RoadmapJobRequest {
        @NotEmpty(message = "analysisIds는 최소 1개 필요합니다.")
        @Size(max = 3, message = "analysisIds는 최대 3개까지 지정할 수 있습니다.")
        @Schema(description = "완료된 코드 분석 ID 목록 (1~3개)", example = "[\"uuid-1\", \"uuid-2\"]")
        private List<String> analysisIds;

        @Schema(description = "여러 분석 결과를 합산해 단일 로드맵 반환 여부 (기본 false)", example = "false")
        private boolean merge = false;
    }

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
        @Size(max = 100, message = "jobPosition은 100자를 초과할 수 없습니다.")
        @Pattern(
                regexp = "^[a-zA-Z0-9가-힣\\s,.~/&+\\-\\(\\)]+$",
                message = "jobPosition에 허용되지 않는 문자가 포함되어 있습니다."
        )
        @Schema(description = "직무 포지션", example = "백엔드 개발자")
        private String jobPosition;

        @NotBlank(message = "career는 필수입니다.")
        @Size(max = 100, message = "career는 100자를 초과할 수 없습니다.")
        @Pattern(
                regexp = "^[a-zA-Z0-9가-힣\\s,.~/&+\\-\\(\\)]+$",
                message = "career에 허용되지 않는 문자가 포함되어 있습니다."
        )
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "AI 작업 상태 조회 응답")
    public static class JobStatusResponse {
        @Schema(description = "BE 내부 작업 ID", example = "1")
        private Long jobId;

        @Schema(description = "작업 상태 (PENDING / DONE / ERROR)", example = "DONE")
        private String status;

        @Schema(description = "완료된 output PDF URL (PDF 작업 DONE 상태일 때만 non-null)", nullable = true)
        private String outputPdfUrl;

        @Schema(description = "로드맵 평가 결과 JSON 문자열 (로드맵 작업 DONE 상태일 때만 non-null)", nullable = true)
        private String resultJson;

        @Schema(description = "오류 메시지 (ERROR 상태일 때만 non-null)", nullable = true)
        private String errorMessage;
    }

    // ============================================================
    // SSE 푸시 페이로드
    // ============================================================

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "AI 작업 완료 SSE 이벤트 페이로드")
    public static class AiJobSsePayload {
        @Schema(description = "BE 내부 작업 ID", example = "1")
        private Long jobId;

        @Schema(description = "작업 상태 (DONE / ERROR)", example = "DONE")
        private String status;

        @Schema(description = "완료된 output PDF URL (PDF 작업 DONE 상태일 때)", nullable = true)
        private String outputPdfUrl;

        @Schema(description = "로드맵 평가 결과 JSON 문자열 (로드맵 작업 DONE 상태일 때)", nullable = true)
        private String resultJson;
    }
}
