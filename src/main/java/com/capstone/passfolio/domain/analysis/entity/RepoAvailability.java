package com.capstone.passfolio.domain.analysis.entity;

import com.capstone.passfolio.common.auditor.TimeBaseEntity;
import com.capstone.passfolio.domain.analysis.entity.enums.RepoAvailabilityStatus;
import com.capstone.passfolio.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * repo 사전 분석 가능성(Precheck) 결과 — (user, repo_url)당 1행(UNIQUE).
 * 분석 실행 행(ProjectAnalysis)과 완전히 분리: 이 테이블은 "이 repo가 분석 가능한가"만 표현한다.
 * Lambda(PRECHECK)가 clone 후 작업트리/.git 크기를 재서 status를 전환한다.
 */
@Entity
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "repo_availability",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_repo_availability_user_repo", columnNames = {"user_id", "repo_url"}),
        indexes = @Index(name = "idx_repo_availability_user_id", columnList = "user_id")
)
public class RepoAvailability extends TimeBaseEntity {

    @Id
    private String id; // UUID — precheck 상관키(Lambda 콜백 correlation)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "repo_url", nullable = false, length = 2048)
    private String repoUrl;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RepoAvailabilityStatus status = RepoAvailabilityStatus.CHECKING;

    @Column(name = "worktree_mb")
    private Double worktreeMb; // 작업트리 MB(점검 완료 시)

    @Column(name = "git_history_mb")
    private Double gitHistoryMb; // .git 히스토리 MB(점검 완료 시)

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason; // DISABLED 사유

    /** 재점검 디스패치: 점검 중으로 전환(이전 사유 초기화). */
    public void markChecking() {
        this.status = RepoAvailabilityStatus.CHECKING;
        this.reason = null;
    }

    /** 점검 결과: 분석 가능. */
    public void markAvailable(Double worktreeMb, Double gitHistoryMb) {
        this.status = RepoAvailabilityStatus.AVAILABLE;
        this.worktreeMb = worktreeMb;
        this.gitHistoryMb = gitHistoryMb;
        this.reason = null;
    }

    /** 점검 결과: 분석 불가(사유 보유). 크기 초과 시 측정값도 함께 저장. */
    public void markDisabled(String reason, Double worktreeMb, Double gitHistoryMb) {
        this.status = RepoAvailabilityStatus.DISABLED;
        this.reason = reason;
        this.worktreeMb = worktreeMb;
        this.gitHistoryMb = gitHistoryMb;
    }
}
