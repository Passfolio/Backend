package com.capstone.passfolio.domain.analysis.controller;

import com.capstone.passfolio.domain.analysis.dto.ProjectAnalysisDto;
import com.capstone.passfolio.domain.analysis.service.ProjectAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/project-analysis")
public class ProjectAnalysisController implements ProjectAnalysisApiSpecification {

    private final ProjectAnalysisService projectAnalysisService;

    // Lambda 완료 콜백. 인증은 InternalApiKeyFilter(X-INTERNAL-API-KEY)가 필터 단계에서 처리.
    @Override
    @PostMapping("/webhook")
    public ResponseEntity<Void> completeAnalysis(@Valid @RequestBody ProjectAnalysisDto.WebhookCompleteRequest request) {
        projectAnalysisService.completeAnalysis(request);
        return ResponseEntity.ok().build();
    }
}
