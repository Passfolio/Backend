package com.capstone.passfolio.domain.analysis.service;

import com.capstone.passfolio.domain.analysis.entity.ProjectAnalysis;
import com.capstone.passfolio.domain.analysis.entity.enums.AnalysisFlag;
import com.capstone.passfolio.domain.analysis.repository.ProjectAnalysisRepository;
import com.capstone.passfolio.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ProjectAnalysis 쓰기 트랜잭션 경계(AiJobWriter 패턴). 디스패치 오케스트레이션(외부호출)은
 * 트랜잭션 밖에서 수행하고, 짧은 상태변경만 여기서 커밋한다(자기호출 프록시 우회 방지).
 */
@Service
@RequiredArgsConstructor
public class ProjectAnalysisWriter {

    private final ProjectAnalysisRepository projectAnalysisRepository;

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
