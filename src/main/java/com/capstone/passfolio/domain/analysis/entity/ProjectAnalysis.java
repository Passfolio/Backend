package com.capstone.passfolio.domain.analysis.entity;

import com.capstone.passfolio.common.auditor.TimeBaseEntity;
import com.capstone.passfolio.domain.analysis.entity.enums.AnalysisFlag;
import com.capstone.passfolio.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "project_analysis",
        indexes = {
                // 사용자 정보로 좁힌 뒤 repo_url 필터하는 조회 경로의 보조 인덱스.
                // (repo_url=VARCHAR(2048)는 btree 크기 한도 위험 → user_id 단일 인덱스로 둠)
                @Index(name = "idx_project_analysis_user_id", columnList = "user_id")
        }
)
public class ProjectAnalysis extends TimeBaseEntity {
    @Id
    private String id; // Surrogate Key — analysis_id(외부에서 발급). Lambda 이벤트/완료 콜백의 상관키.

    @Column(name = "repo_url", nullable = false, length = 2048)
    private String repoUrl;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_flag", nullable = false, length = 16)
    private AnalysisFlag analysisFlag = AnalysisFlag.YET;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "result_cdn_url", columnDefinition = "TEXT")
    private String resultCdnUrl; // DONE 시 결과 산출물 CDN URL

    @Column(name = "service_name")
    private String serviceName; // DONE 시 분석된 프로젝트 서비스명

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason; // FAILED 시 사유

    /** 디스패치 시점: 분석 진행 중으로 전환. */
    public void markInProgress() {
        this.analysisFlag = AnalysisFlag.IN_PROGRESS;
    }

    /** 완료 콜백: 결과 CDN URL·서비스명 저장 후 완료로 전환. */
    public void markDone(String resultCdnUrl, String serviceName) {
        this.analysisFlag = AnalysisFlag.DONE;
        this.resultCdnUrl = resultCdnUrl;
        this.serviceName = serviceName;
    }

    /** 완료 콜백(실패): 사유 저장 후 실패로 전환. */
    public void markFailed(String failureReason) {
        this.analysisFlag = AnalysisFlag.FAILED;
        this.failureReason = failureReason;
    }
}
