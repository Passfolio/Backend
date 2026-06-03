package com.capstone.passfolio.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.capstone.passfolio.domain.ai.client.AiApiClient;
import com.capstone.passfolio.domain.ai.dto.AiDto;
import com.capstone.passfolio.domain.ai.entity.AiJob;
import com.capstone.passfolio.domain.ai.entity.AiJobStatus;
import com.capstone.passfolio.domain.ai.entity.AiJobType;
import com.capstone.passfolio.domain.ai.repository.AiJobRepository;
import com.capstone.passfolio.domain.analysis.entity.ProjectAnalysis;
import com.capstone.passfolio.domain.analysis.entity.enums.AnalysisFlag;
import com.capstone.passfolio.domain.analysis.repository.ProjectAnalysisRepository;
import com.capstone.passfolio.domain.analysis.service.BatchPortfolioStore;
import com.capstone.passfolio.domain.analysis.service.SmsNotifier;
import com.capstone.passfolio.domain.file.service.FileService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiJobService — NONSTOP 포폴 핸드오프/완료 + 로드맵 평가 로직")
class AiJobServiceTest {

    @Mock private FileService fileService;
    @Mock private AiApiClient aiApiClient;
    @Mock private AiJobRepository aiJobRepository;
    @Mock private AiJobWriter aiJobWriter;
    @Mock private AiSseService aiSseService;
    @Mock private BatchPortfolioStore batchPortfolioStore;
    @Mock private SmsNotifier smsNotifier;
    @Mock private com.capstone.passfolio.common.notification.DiscordNotifier discordNotifier;
    @Mock private ProjectAnalysisRepository projectAnalysisRepository;

    @InjectMocks private AiJobService service;

    private static AiDto.AiJobInitResponse aiResp(String jobId) {
        return AiDto.AiJobInitResponse.builder().jobId(jobId).build();
    }

    // ============================================================
    // NONSTOP 포폴 핸드오프 — 기존 테스트
    // ============================================================

    @Test
    @DisplayName("EDIT → upgradePortfolio(from-pdf)에 code_analysis_urls 전달 + AiJob(PORTFOLIO_FROM_PDF) 등록")
    void startPortfolioFromAnalyses_edit() {
        List<String> urls = List.of("https://cdn/a.json", "https://cdn/b.json");
        given(aiJobWriter.createPendingJob(eq(1L), isNull(), eq(AiJobType.PORTFOLIO_FROM_PDF))).willReturn(10L);
        given(aiApiClient.upgradePortfolio("pdf-url", urls, 1L)).willReturn(aiResp("ai-1"));

        Long jobId = service.startPortfolioFromAnalyses(1L, "pdf-url", "EDIT", urls);

        assertThat(jobId).isEqualTo(10L);
        then(aiApiClient).should().upgradePortfolio("pdf-url", urls, 1L);
        then(aiApiClient).should(never()).generatePortfolioFromCoverLetter(any());
        then(aiJobWriter).should().assignAiJobId(10L, "ai-1");
    }

    @Test
    @DisplayName("GENERATE → generatePortfolioFromCoverLetter(from-cover-letter)에 pdf_url·code_analysis_urls 전달")
    void startPortfolioFromAnalyses_generate() {
        List<String> urls = List.of("https://cdn/a.json");
        given(aiJobWriter.createPendingJob(eq(1L), isNull(), eq(AiJobType.PORTFOLIO_FROM_COVER_LETTER))).willReturn(11L);
        given(aiApiClient.generatePortfolioFromCoverLetter(any())).willReturn(aiResp("ai-2"));

        Long jobId = service.startPortfolioFromAnalyses(1L, "cover-url", "GENERATE", urls);

        assertThat(jobId).isEqualTo(11L);
        ArgumentCaptor<AiDto.AiCoverLetterRequest> cap = ArgumentCaptor.forClass(AiDto.AiCoverLetterRequest.class);
        then(aiApiClient).should().generatePortfolioFromCoverLetter(cap.capture());
        assertThat(cap.getValue().getPdfUrl()).isEqualTo("cover-url");
        assertThat(cap.getValue().getCodeAnalysisUrls()).isEqualTo(urls);
        then(aiApiClient).should(never()).upgradePortfolio(any(), any(), any());
        then(aiJobWriter).should().assignAiJobId(11L, "ai-2");
    }

    @Test
    @DisplayName("completeJob DONE — NONSTOP 포폴(batch 매핑 존재)이면 SMS 통지 + SSE")
    void completeJob_nonstopPortfolio_sendsSms() {
        AiJob job = mock(AiJob.class);
        given(job.getStatus()).willReturn(AiJobStatus.PENDING);
        given(job.getId()).willReturn(20L);
        given(job.getUserId()).willReturn(1L);
        given(aiJobRepository.findByAiJobId("ai-x")).willReturn(Optional.of(job));
        given(batchPortfolioStore.readBatchByJob(20L)).willReturn("batch-1");

        service.completeJob(new AiDto.JobCompleteRequest(
                "ai-x", "DONE", "https://bucket.s3.ap-northeast-2.amazonaws.com/files/ai/1/p.pdf", null));

        then(job).should().markDone(anyString());
        then(smsNotifier).should().notifyPortfolioCompleted(1L, "batch-1", true);
        then(aiSseService).should().push(eq(1L), eq(20L), any(), any());
    }

    @Test
    @DisplayName("completeJob DONE — 일반 /upload 작업(batch 매핑 없음)이면 SMS 미발송")
    void completeJob_uploadJob_noSms() {
        AiJob job = mock(AiJob.class);
        given(job.getStatus()).willReturn(AiJobStatus.PENDING);
        given(job.getId()).willReturn(21L);
        given(job.getUserId()).willReturn(2L);
        given(aiJobRepository.findByAiJobId("ai-y")).willReturn(Optional.of(job));
        given(batchPortfolioStore.readBatchByJob(21L)).willReturn(null);

        service.completeJob(new AiDto.JobCompleteRequest(
                "ai-y", "DONE", "https://bucket.s3.ap-northeast-2.amazonaws.com/files/ai/2/p.pdf", null));

        then(smsNotifier).should(never()).notifyPortfolioCompleted(any(), any(), any(Boolean.class));
        then(aiSseService).should().push(eq(2L), eq(21L), any(), any());
    }

    // ============================================================
    // 로드맵 평가 — 신규 테스트
    // ============================================================

    @Test
    @DisplayName("startRoadmapAssess — DONE 분석의 CDN URL을 assessRoadmap에 전달, ai_job_id는 BE 생성 UUID")
    void startRoadmapAssess_validAnalysisIds_dispatchesToFastApi() {
        ProjectAnalysis done = mock(ProjectAnalysis.class);
        given(done.getAnalysisFlag()).willReturn(AnalysisFlag.DONE);
        given(done.getResultCdnUrl()).willReturn("https://cdn/a.json");
        given(projectAnalysisRepository.findAllByIdInAndUser_Id(List.of("uuid-1"), 1L))
                .willReturn(List.of(done));
        given(aiJobWriter.createPendingJob(eq(1L), isNull(), eq(AiJobType.ROADMAP_FROM_ANALYSES)))
                .willReturn(30L);

        service.startRoadmapAssess(1L, List.of("uuid-1"), false);

        ArgumentCaptor<AiDto.AiRoadmapRequest> cap = ArgumentCaptor.forClass(AiDto.AiRoadmapRequest.class);
        then(aiApiClient).should().assessRoadmap(cap.capture());
        assertThat(cap.getValue().getCodeAnalysisUrls()).containsExactly("https://cdn/a.json");
        assertThat(cap.getValue().getAiJobId()).isNotBlank();
        then(aiJobWriter).should().assignAiJobId(eq(30L), anyString());
    }

    @Test
    @DisplayName("startRoadmapAssess — 타인 소유 또는 미완료 analysisId는 필터링되어 codeAnalysisUrls=null")
    void startRoadmapAssess_unownedOrNotDone_passesNullUrls() {
        given(projectAnalysisRepository.findAllByIdInAndUser_Id(List.of("bad-id"), 1L))
                .willReturn(List.of());
        given(aiJobWriter.createPendingJob(eq(1L), isNull(), eq(AiJobType.ROADMAP_FROM_ANALYSES)))
                .willReturn(31L);

        service.startRoadmapAssess(1L, List.of("bad-id"), false);

        ArgumentCaptor<AiDto.AiRoadmapRequest> cap = ArgumentCaptor.forClass(AiDto.AiRoadmapRequest.class);
        then(aiApiClient).should().assessRoadmap(cap.capture());
        assertThat(cap.getValue().getCodeAnalysisUrls()).isNull();
    }

    @Test
    @DisplayName("completeRoadmapJob DONE + result → markDoneWithResult 호출, pushRoadmap SSE 전송")
    void completeRoadmapJob_done_storesResultAndPushesRoadmapSse() throws Exception {
        AiJob job = mock(AiJob.class);
        given(job.getStatus()).willReturn(AiJobStatus.PENDING);
        given(job.getId()).willReturn(32L);
        given(job.getUserId()).willReturn(1L);
        given(job.getResultJson()).willReturn("[{\"service_name\":\"test\"}]");
        given(aiJobRepository.findByAiJobId("ai-roadmap")).willReturn(Optional.of(job));

        JsonNode result = new ObjectMapper().readTree("[{\"service_name\":\"test\"}]");
        service.completeRoadmapJob(new AiDto.RoadmapCompleteRequest("ai-roadmap", "DONE", result, null));

        then(job).should().markDoneWithResult(anyString());
        then(aiSseService).should().pushRoadmap(eq(1L), eq(32L), anyString(), anyString());
        then(aiSseService).should(never()).push(any(), any(), any(), any());
    }

    @Test
    @DisplayName("completeRoadmapJob DONE + result없음 → ERROR 처리")
    void completeRoadmapJob_doneWithNoResult_forcesError() {
        AiJob job = mock(AiJob.class);
        given(job.getStatus()).willReturn(AiJobStatus.PENDING);
        given(job.getId()).willReturn(33L);
        given(job.getUserId()).willReturn(1L);
        given(aiJobRepository.findByAiJobId("ai-empty")).willReturn(Optional.of(job));

        service.completeRoadmapJob(new AiDto.RoadmapCompleteRequest("ai-empty", "DONE", null, null));

        then(job).should().markError(anyString());
        then(job).should(never()).markDoneWithResult(any());
    }
}
