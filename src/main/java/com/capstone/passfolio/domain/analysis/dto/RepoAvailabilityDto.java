package com.capstone.passfolio.domain.analysis.dto;

import com.capstone.passfolio.domain.analysis.entity.RepoAvailability;
import com.capstone.passfolio.domain.analysis.entity.enums.RepoAvailabilityStatus;
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

public class RepoAvailabilityDto {

    // ============================================================
    // FE → BE : 사전 점검 시작 요청 / 응답
    // ============================================================
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "repo 사전 점검 요청(최대 5개)")
    public static class PrecheckStartRequest {
        @NotEmpty(message = "repoUrls는 최소 1개 필요합니다.")
        @Size(max = 5, message = "한 번에 점검 가능한 저장소는 최대 5개입니다.")
        @Schema(description = "점검할 GitHub 저장소 URL 목록(1~5개)")
        private List<@NotBlank @URL(message = "repoUrl이 유효한 URL 형식이 아닙니다.") String> repoUrls;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "사전 점검 시작 응답(점검 디스패치된 항목)")
    public static class PrecheckStartResponse {
        private List<PrecheckItem> items;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "사전 점검 항목(현재 상태)")
    public static class PrecheckItem {
        private String precheckId;
        private String repoUrl;
        private String status;          // CHECKING / AVAILABLE / DISABLED
        private Double worktreeMb;
        private Double gitHistoryMb;
        private String reason;
        private LocalDateTime lastModifiedAt;

        public static PrecheckItem from(RepoAvailability r) {
            return PrecheckItem.builder()
                    .precheckId(r.getId())
                    .repoUrl(r.getRepoUrl())
                    .status(r.getStatus().name())
                    .worktreeMb(r.getWorktreeMb())
                    .gitHistoryMb(r.getGitHistoryMb())
                    .reason(r.getReason())
                    .lastModifiedAt(r.getLastModifiedAt())
                    .build();
        }
    }

    // ============================================================
    // BE → Lambda SQS 작업 메시지 (PRECHECK, snake_case)
    // ============================================================
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PrecheckJobMessage {
        private String precheckId;       // = repo_availability 행 id(콜백 상관키)
        private String repoUrl;
        private String userPk;
        private boolean isPrivate;
        private String encryptedToken;   // private repo clone용(KMS base64), 공개면 null
        @Builder.Default
        private String mode = "PRECHECK"; // handler 분기 키
    }

    // ============================================================
    // Lambda → BE Webhook (사전 점검 결과 콜백, snake_case, 내부 호출)
    // ============================================================
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(description = "사전 점검 결과 콜백 (Lambda → BE, 내부 호출)")
    public static class PrecheckWebhookRequest {
        @NotBlank(message = "precheckId는 필수입니다.")
        private String precheckId;
        @NotBlank(message = "status는 필수입니다.")
        private String status;           // AVAILABLE / DISABLED
        private Double worktreeMb;
        private Double gitHistoryMb;
        private String reason;
    }

    // ============================================================
    // SSE 푸시 페이로드 (BE → FE)
    // ============================================================
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "사전 점검 결과 SSE 페이로드")
    public static class PrecheckSsePayload {
        private String repoUrl;
        private String status;          // AVAILABLE / DISABLED
        private Double worktreeMb;
        private Double gitHistoryMb;
        private String reason;

        public static PrecheckSsePayload from(RepoAvailability r) {
            return PrecheckSsePayload.builder()
                    .repoUrl(r.getRepoUrl())
                    .status(r.getStatus().name())
                    .worktreeMb(r.getWorktreeMb())
                    .gitHistoryMb(r.getGitHistoryMb())
                    .reason(r.getReason())
                    .build();
        }
    }

    // status 문자열 → enum 매핑(웹훅 입력 정규화). 알 수 없으면 DISABLED로 안전 처리.
    public static RepoAvailabilityStatus parseStatus(String raw) {
        if (raw == null) return RepoAvailabilityStatus.DISABLED;
        try {
            return RepoAvailabilityStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return RepoAvailabilityStatus.DISABLED;
        }
    }
}
