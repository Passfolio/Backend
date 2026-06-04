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

import com.capstone.passfolio.domain.ai.client.AiApiClient;
import com.capstone.passfolio.domain.ai.dto.AiDto;
import com.capstone.passfolio.domain.ai.entity.AiJob;
import com.capstone.passfolio.domain.ai.entity.AiJobStatus;
import com.capstone.passfolio.domain.ai.entity.AiJobType;
import com.capstone.passfolio.domain.ai.repository.AiJobRepository;
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
@DisplayName("AiJobService — NONSTOP 포폴 핸드오프/완료 로직")
class AiJobServiceTest {

    @Mock private FileService fileService;
    @Mock private AiApiClient aiApiClient;
    @Mock private AiJobRepository aiJobRepository;
    @Mock private AiJobWriter aiJobWriter;
    @Mock private AiSseService aiSseService;
    @Mock private BatchPortfolioStore batchPortfolioStore;
    @Mock private SmsNotifier smsNotifier;
    @Mock private com.capstone.passfolio.common.notification.DiscordNotifier discordNotifier;

    @InjectMocks private AiJobService service;

    private static AiDto.AiJobInitResponse aiResp(String jobId) {
        return AiDto.AiJobInitResponse.builder().jobId(jobId).build();
    }

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
        then(smsNotifier).should().notifyPortfolioCompleted(eq(1L), eq("batch-1"), eq(true), any());
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

        then(smsNotifier).should(never()).notifyPortfolioCompleted(any(), any(), any(Boolean.class), any());
        then(aiSseService).should().push(eq(2L), eq(21L), any(), any());
    }
}
