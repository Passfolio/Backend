package com.capstone.passfolio.domain.ai.controller;

import com.capstone.passfolio.domain.ai.dto.AiDto;
import com.capstone.passfolio.domain.ai.service.AiJobService;
import com.capstone.passfolio.domain.ai.service.AiSseService;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai")
public class AiController implements AiApiSpecification {

    private final AiJobService aiJobService;
    private final AiSseService aiSseService;

    @Override
    @PostMapping("/jobs/portfolio/from-pdf")
    public ResponseEntity<AiDto.JobInitResponse> startPortfolioFromPdf(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody AiDto.PdfJobRequest request) {
        AiDto.JobInitResponse response =
                aiJobService.startPortfolioFromPdf(userPrincipal.getUserId(), request.getFileId());
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/jobs/cover-letter/from-pdf")
    public ResponseEntity<AiDto.JobInitResponse> startCoverLetterFromPdf(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody AiDto.PdfJobRequest request) {
        AiDto.JobInitResponse response =
                aiJobService.startCoverLetterFromPdf(userPrincipal.getUserId(), request.getFileId());
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/jobs/cover-letter/from-portfolio")
    public ResponseEntity<AiDto.JobInitResponse> startCoverLetterFromPortfolio(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody AiDto.CoverLetterJobRequest request) {
        AiDto.JobInitResponse response = aiJobService.startCoverLetterFromPortfolio(
                userPrincipal.getUserId(), request.getFileId(),
                request.getJobPosition(), request.getCareer());
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping({"/jobs/portfolio/from-cover-letter"})
    public ResponseEntity<AiDto.JobInitResponse> startPortfolioFromCoverLetter(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody AiDto.CoverLetterJobRequest request) {
        AiDto.JobInitResponse response = aiJobService.startPortfolioFromCoverLetter(
                userPrincipal.getUserId(), request.getFileId(),
                request.getJobPosition(), request.getCareer());
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/jobs/roadmap/assess")
    public ResponseEntity<AiDto.JobInitResponse> startRoadmapAssess(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody AiDto.RoadmapJobRequest request) {
        AiDto.JobInitResponse response = aiJobService.startRoadmapAssess(
                userPrincipal.getUserId(), request.getAnalysisIds(), request.isMerge());
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<AiDto.JobStatusResponse> getJobStatus(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long jobId) {
        AiDto.JobStatusResponse response =
                aiJobService.getJobStatus(userPrincipal.getUserId(), jobId);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/jobs/complete")
    public ResponseEntity<Void> completeJob(@Valid @RequestBody AiDto.JobCompleteRequest request) {
        aiJobService.completeJob(request);
        return ResponseEntity.ok().build();
    }

    @Override
    @PostMapping("/roadmap/complete")
    public ResponseEntity<Void> completeRoadmapJob(@Valid @RequestBody AiDto.RoadmapCompleteRequest request) {
        aiJobService.completeRoadmapJob(request);
        return ResponseEntity.ok().build();
    }

    @Override
    @GetMapping("/jobs/subscribe")
    public SseEmitter subscribeToJobStatus(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return aiSseService.subscribe(userPrincipal.getUserId());
    }
}
