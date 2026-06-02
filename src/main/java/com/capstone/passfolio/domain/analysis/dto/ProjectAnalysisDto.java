package com.capstone.passfolio.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;
import java.util.List;

public class ProjectAnalysisDto {

    // ============================================================
    // FE → BE 분석 시작 요청 / 응답
    // ============================================================
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "프로젝트 분석 시작 요청(다중 repo, 최대 3개)")
    public static class StartRequest {
        @NotEmpty(message = "repoUrls는 최소 1개 필요합니다.")
        @Size(max = 3, message = "한 번에 요청 가능한 저장소는 최대 3개입니다.")
        @Schema(description = "분석할 GitHub 저장소 URL 목록(1~3개)",
                example = "[\"https://github.com/owner/repo1\", \"https://github.com/owner/repo2\"]")
        private List<@NotBlank @URL(message = "repoUrl이 유효한 URL 형식이 아닙니다.") String> repoUrls;

        @Schema(description = "분석 모드 (NONSTOP=완료 후 포트폴리오 생성 / STEP=분석만). 기본 NONSTOP",
                example = "NONSTOP", nullable = true)
        private String mode;

        @Size(max = 20, message = "phone은 20자를 초과할 수 없습니다.")
        @Schema(description = "배치 완료 SMS 수신 번호(선택, E.164 권장). DB 미저장·transient.",
                example = "+821012345678", nullable = true)
        private String phone;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "프로젝트 분석 시작 응답(배치)")
    public static class StartResponse {
        @Schema(description = "배치 ID", example = "batch-uuid")
        private String batchId;

        @Schema(description = "디스패치된 분석 목록")
        private List<DispatchedItem> analyses;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "디스패치된 개별 분석")
    public static class DispatchedItem {
        private String analysisId;
        private String repoUrl;
        private String status; // IN_PROGRESS / FAILED(디스패치 실패)
    }

    // ============================================================
    // ADMIN 전용 테스트 디스패치 (공개 repo 다수, token 없이 — 파이프라인/메트릭 부하 테스트)
    // ============================================================
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "ADMIN 분석 부하 테스트 요청(공개 repo, 토큰 불필요, 최대 20개)")
    public static class AdminTestBatchRequest {
        @NotEmpty(message = "repoUrls는 최소 1개 필요합니다.")
        @Size(max = 300, message = "한 번에 요청 가능한 저장소는 최대 300개입니다.")
        @Schema(description = "분석할 공개 GitHub 저장소 URL 목록(1~300개)")
        private List<@NotBlank @URL(message = "repoUrl이 유효한 URL 형식이 아닙니다.") String> repoUrls;

        @Schema(description = "분석 모드(기본 STEP — FastAPI 핸드오프 없이 분석만).", nullable = true)
        private String mode;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "ADMIN 테스트 디스패치 응답")
    public static class AdminTestBatchResponse {
        private String batchId;
        private List<AdminDispatchedItem> analyses;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "ADMIN 테스트 디스패치 한도(호출자 기준)")
    public static class AdminTestLimitResponse {
        private int maxRepoCount; // 이 호출자가 한 번에 디스패치 가능한 최대 repo 수
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "ADMIN 테스트로 디스패치된 개별 분석(디스패치 순번 포함)")
    public static class AdminDispatchedItem {
        private String analysisId;
        private String repoUrl;
        private String owner;        // dominant 모드 username(=repo owner)
        private int dispatchSeq;     // 디스패치 순번(0-based)
        private String status;       // IN_PROGRESS / FAILED(디스패치 실패)
    }

    // ============================================================
    // ADMIN 배치 상태 폴링 (FE 대시보드가 5초 주기로 조회 — 상태 레벨 메트릭)
    // ============================================================
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "ADMIN 배치 상태 폴링 응답(project_analysis 상태 레벨 집계)")
    public static class AdminBatchStatusResponse {
        private String batchId;
        private int total;
        private boolean allTerminal;                  // done+failed == total(폴링 종료 신호)
        private BatchStatusCounts counts;
        private List<AdminBatchAnalysisItem> analyses;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "배치 상태 카운트(analysis_flag별)")
    public static class BatchStatusCounts {
        private int yet;
        private int inProgress;
        private int done;
        private int failed;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "배치 내 개별 분석 상태")
    public static class AdminBatchAnalysisItem {
        private String analysisId;
        private String repoUrl;
        private String status;            // YET / IN_PROGRESS / DONE / FAILED
        private String serviceName;       // DONE 시
        private String cdnUrl;            // DONE 시
        private String failureReason;     // FAILED 시
        private LocalDateTime createdAt;
        private LocalDateTime lastModifiedAt;
    }

    // ============================================================
    // SSE 푸시 페이로드 (BE → FE)
    // ============================================================
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "개별 repo 분석 완료 SSE 페이로드")
    public static class AnalysisSsePayload {
        private String analysisId;
        private String batchId;
        private String status;       // DONE / FAILED
        private String repoUrl;
        private String cdnUrl;       // DONE 시
        private String serviceName;  // DONE 시
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "배치 전체 완료 SSE 페이로드")
    public static class BatchSsePayload {
        private String batchId;
        private String status;             // ALL_DONE(전원 성공·핸드오프) / BATCH_FAILED(일부 실패)
        private int total;
        private int failures;
        private boolean portfolioRequested; // FastAPI 핸드오프 여부(NONSTOP·전원성공일 때만 true)
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
        // admin 테스트 디스패치 전용: Lambda p1이 dominant 기여자 모드로 해석(핸들↔git신원 불일치 흡수).
        // 일반 /start 경로는 false(미설정) → 무회귀.
        @JsonProperty("dominant_fallback")
        private boolean dominantFallback;
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
