package com.capstone.passfolio.domain.analysis.service;

import com.capstone.passfolio.domain.analysis.dto.ProjectAnalysisDto;
import com.capstone.passfolio.domain.analysis.entity.ProjectAnalysis;
import com.capstone.passfolio.domain.analysis.entity.enums.AnalysisFlag;
import com.capstone.passfolio.domain.analysis.repository.ProjectAnalysisRepository;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectAnalysisService {

    private final ProjectAnalysisRepository projectAnalysisRepository;

    // Lambda가 보내는 완료 상태 토큰(대소문자 무시) → 성공으로 간주.
    private static final Set<String> SUCCESS_TOKENS = Set.of("analyzed", "done", "success");

    /**
     * 분석 완료 콜백 처리(Lambda → BE). 멱등성 보장 — 이미 종료(DONE/FAILED)된 건은 건너뜀.
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeAnalysis(ProjectAnalysisDto.WebhookCompleteRequest dto) {
        MDC.put("analysisId", dto.getAnalysisId());
        try {
            ProjectAnalysis analysis = projectAnalysisRepository.findById(dto.getAnalysisId())
                    .orElseThrow(() -> new RestException(ErrorCode.PROJECT_ANALYSIS_NOT_FOUND));

            if (isTerminal(analysis.getAnalysisFlag())) {
                log.info("[ProjectAnalysisService] completeAnalysis skipped (already {}). analysisId={}",
                        analysis.getAnalysisFlag(), dto.getAnalysisId());
                return;
            }

            boolean success = dto.getStatus() != null
                    && SUCCESS_TOKENS.contains(dto.getStatus().trim().toLowerCase());

            if (success) {
                if (dto.getCdnUrl() == null || dto.getCdnUrl().isBlank()) {
                    log.warn("[ProjectAnalysisService] success with no cdnUrl, forcing FAILED. analysisId={}",
                            dto.getAnalysisId());
                    analysis.markFailed("완료 보고됐으나 결과 CDN URL이 없습니다.");
                    return;
                }
                analysis.markDone(dto.getCdnUrl(), dto.getServiceName());
                log.info("[ProjectAnalysisService] analysis DONE. analysisId={}, service={}",
                        dto.getAnalysisId(), dto.getServiceName());
            } else {
                analysis.markFailed(dto.getErrorMessage());
                log.info("[ProjectAnalysisService] analysis FAILED. analysisId={}, status={}",
                        dto.getAnalysisId(), dto.getStatus());
            }
        } finally {
            MDC.remove("analysisId");
        }
    }

    private boolean isTerminal(AnalysisFlag flag) {
        return flag == AnalysisFlag.DONE || flag == AnalysisFlag.FAILED;
    }
}
