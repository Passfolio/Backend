package com.capstone.passfolio.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

public class ProjectAnalysisDto {

    // ============================================================
    // FE → BE 분석 시작 요청 / 응답
    // ============================================================
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "프로젝트 분석 시작 요청")
    public static class StartRequest {
        @NotBlank(message = "repoUrl은 필수입니다.")
        @URL(message = "repoUrl이 유효한 URL 형식이 아닙니다.")
        @Schema(description = "분석할 GitHub 저장소 URL", example = "https://github.com/owner/repo")
        private String repoUrl;

        @Schema(description = "분석 모드 (NONSTOP=완료 후 포트폴리오 생성 / STEP=분석만). 기본 NONSTOP",
                example = "NONSTOP", nullable = true)
        private String mode;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "프로젝트 분석 시작 응답")
    public static class StartResponse {
        @Schema(description = "분석 ID", example = "a1b2c3")
        private String analysisId;

        @Schema(description = "현재 상태", example = "IN_PROGRESS")
        private String status;
    }

    // ============================================================
    // BE → Lambda SQS 작업 메시지 (Lambda 이벤트 포맷, snake_case)
    // ============================================================
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class LambdaJobMessage {
        private String analysisId;
        private String githubUsername;
        private String repoUrl;
        private String userPk;
        @JsonProperty("is_private")
        private boolean isPrivate;
        private double repoSizeMb;
        private String encryptedToken; // KMS base64 ciphertext
        private String mode;
    }

    // ============================================================
    // Lambda → BE Webhook (분석 완료 콜백)
    //  - Lambda 이벤트/콜백은 snake_case (analysis_id, cdn_url, ...)
    //  - 페이로드의 추가 필드(user_pk·github_username·repo_url·s3_key·mode)는 무시(Spring 기본 설정)
    // ============================================================
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "프로젝트 분석 완료 콜백 요청 (Lambda → BE, 내부 호출)")
    public static class WebhookCompleteRequest {
        @NotBlank(message = "analysisId는 필수입니다.")
        @Schema(description = "분석 ID (ProjectAnalysis PK와 동일)", example = "a1b2c3")
        private String analysisId;

        @NotBlank(message = "status는 필수입니다.")
        @Schema(description = "완료 상태 (analyzed/DONE → 완료, failed/ERROR → 실패)", example = "analyzed")
        private String status;

        @URL(message = "cdnUrl이 유효한 URL 형식이 아닙니다.")
        @Schema(description = "결과 산출물 CDN URL (완료 시)", nullable = true)
        private String cdnUrl;

        @Schema(description = "분석된 프로젝트 서비스명 (완료 시)", nullable = true)
        private String serviceName;

        @Schema(description = "오류 메시지 (실패 시)", nullable = true)
        private String errorMessage;
    }
}
