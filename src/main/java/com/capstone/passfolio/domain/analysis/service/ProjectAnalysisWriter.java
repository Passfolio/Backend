package com.capstone.passfolio.domain.analysis.service;

import com.capstone.passfolio.domain.analysis.dto.ProjectAnalysisDto;
import com.capstone.passfolio.domain.analysis.entity.ProjectAnalysis;
import com.capstone.passfolio.domain.analysis.entity.enums.AnalysisFlag;
import com.capstone.passfolio.domain.analysis.repository.ProjectAnalysisRepository;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * ProjectAnalysis 쓰기 트랜잭션 경계(AiJobWriter 패턴). 디스패치 오케스트레이션(외부호출)은
 * 트랜잭션 밖에서 수행하고, 짧은 상태변경만 여기서 커밋한다(자기호출 프록시 우회 방지).
 */
@Service
@RequiredArgsConstructor
public class ProjectAnalysisWriter {

    private final ProjectAnalysisRepository projectAnalysisRepository;

    private static final Set<String> SUCCESS_TOKENS = Set.of("analyzed", "done", "success");

    /** 완료 콜백의 DB 변경만 트랜잭션으로 처리하고 결과 컨텍스트 반환(통지·배치 처리는 커밋 후 호출부에서).
     *  멱등: 이미 종료(DONE/FAILED)면 null. 미존재면 PROJECT_ANALYSIS_NOT_FOUND. */
    @Transactional
    public CompletionResult applyCompletion(ProjectAnalysisDto.WebhookCompleteRequest dto) {
        ProjectAnalysis a = projectAnalysisRepository.findById(dto.getAnalysisId())
                .orElseThrow(() -> new RestException(ErrorCode.PROJECT_ANALYSIS_NOT_FOUND));
        if (a.getAnalysisFlag() == AnalysisFlag.DONE || a.getAnalysisFlag() == AnalysisFlag.FAILED) {
            return null; // 멱등 skip
        }
        boolean success = dto.getStatus() != null
                && SUCCESS_TOKENS.contains(dto.getStatus().trim().toLowerCase());
        if (success && (dto.getCdnUrl() == null || dto.getCdnUrl().isBlank())) {
            a.markFailed("완료 보고됐으나 결과 CDN URL이 없습니다.");
        } else if (success) {
            a.markDone(dto.getCdnUrl(), dto.getServiceName());
        } else {
            a.markFailed(dto.getErrorMessage());
        }
        Long userId = a.getUser() != null ? a.getUser().getId() : null;
        return new CompletionResult(a.getId(), a.getBatchId(), userId, a.getMode(),
                a.getAnalysisFlag() == AnalysisFlag.FAILED,
                a.getRepoUrl(), a.getResultCdnUrl(), a.getServiceName(), a.getAnalysisFlag().name());
    }

    /** applyCompletion 결과 컨텍스트(커밋 후 SSE/배치/통지에 사용). */
    public record CompletionResult(String analysisId, String batchId, Long userId, String mode,
                                   boolean failed, String repoUrl, String cdnUrl, String serviceName,
                                   String statusName) { }

    @Transactional
    public void createYet(String batchId, String analysisId, String repoUrl, User user, String mode) {
        projectAnalysisRepository.save(ProjectAnalysis.builder()
                .id(analysisId)
                .batchId(batchId)
                .repoUrl(repoUrl)
                .user(user)
                .mode(mode)
                .analysisFlag(AnalysisFlag.YET)
                .build());
    }

    @Transactional
    public void markInProgress(String analysisId) {
        projectAnalysisRepository.findById(analysisId)
                .ifPresent(ProjectAnalysis::markInProgress);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String analysisId, String reason) {
        projectAnalysisRepository.findById(analysisId)
                .ifPresent(a -> a.markFailed(reason));
    }
}
