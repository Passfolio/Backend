package com.capstone.passfolio.domain.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.capstone.passfolio.domain.ai.dto.AiDto;
import com.capstone.passfolio.system.config.http.RestClientConfig;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@Component
public class AiApiClient {

    private final RestClient restClient;
    private final String aiBaseUrl;
    private final ObjectMapper objectMapper;

    public AiApiClient(
            @Qualifier(RestClientConfig.AI_REST_CLIENT) RestClient restClient,
            @Value("${ai.base-url}") String aiBaseUrl,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.aiBaseUrl = aiBaseUrl;
        this.objectMapper = objectMapper;
    }

    // 포트폴리오를 업그레이드한다(NONSTOP: 분석 결과 URL들을 함께 전달 가능, 없으면 rag만).
    public AiDto.AiJobInitResponse upgradePortfolio(String portfolioPdfUrl, java.util.List<String> codeAnalysisUrls, Long userId) {
        int n = codeAnalysisUrls == null ? 0 : codeAnalysisUrls.size();
        log.info("[NONSTOP] FastAPI POST {}/api/v1/portfolio/from-pdf — userId={}, pdfUrl={}, codeAnalysisUrls({})={}",
                aiBaseUrl, userId, portfolioPdfUrl, n, codeAnalysisUrls);
        AiDto.AiJobInitResponse resp = postWithRetry("/portfolio/from-pdf", () -> restClient.post()
                .uri(aiBaseUrl + "/api/v1/portfolio/from-pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .body(AiDto.AiPdfRequest.builder()
                        .pdfUrl(portfolioPdfUrl).userId(userId).codeAnalysisUrls(codeAnalysisUrls).build())
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(AiDto.AiJobInitResponse.class));
        log.info("[NONSTOP] FastAPI 응답 — /portfolio/from-pdf, aiJobId={}", resp != null ? resp.getJobId() : null);
        return resp;
    }

    // 자소서로 자소서를 업그레이드한다.
    public AiDto.AiJobInitResponse upgradeCoverLetter(String coverLetterPdfUrl, Long userId) {
        return postWithRetry("/cover-letter/from-pdf", () -> restClient.post()
                .uri(aiBaseUrl + "/api/v1/cover-letter/from-pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .body(AiDto.AiPdfRequest.builder().pdfUrl(coverLetterPdfUrl).userId(userId).build())
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(AiDto.AiJobInitResponse.class));
    }

    // 포트폴리오로 자소서를 생성한다.
    public AiDto.AiJobInitResponse generateCoverLetterFromPortfolio(AiDto.AiCoverLetterRequest request) {
        return postWithRetry("/cover-letter/from-portfolio", () -> restClient.post()
                .uri(aiBaseUrl + "/api/v1/cover-letter/from-portfolio")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(AiDto.AiJobInitResponse.class));
    }

    // 코드 분석 URL로 로드맵 평가를 시작한다.
    public AiDto.AiJobInitResponse assessRoadmap(AiDto.AiRoadmapRequest request) {
        return postWithRetry("/roadmap/assess", () -> restClient.post()
                .uri(aiBaseUrl + "/api/v1/roadmap/assess")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(AiDto.AiJobInitResponse.class));
    }

    // 자소서로 포트폴리오를 생성한다.
    public AiDto.AiJobInitResponse generatePortfolioFromCoverLetter(AiDto.AiCoverLetterRequest request) {
        int n = request.getCodeAnalysisUrls() == null ? 0 : request.getCodeAnalysisUrls().size();
        log.info("[NONSTOP] FastAPI POST {}/api/v1/portfolio/from-cover-letter — userId={}, pdfUrl={}, jobPosition={}, career={}, codeAnalysisUrls({})={}",
                aiBaseUrl, request.getUserId(), request.getPdfUrl(), request.getJobPosition(), request.getCareer(), n, request.getCodeAnalysisUrls());
        AiDto.AiJobInitResponse resp = postWithRetry("/portfolio/from-cover-letter", () -> restClient.post()
                .uri(aiBaseUrl + "/api/v1/portfolio/from-cover-letter")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(AiDto.AiJobInitResponse.class));
        log.info("[NONSTOP] FastAPI 응답 — /portfolio/from-cover-letter, aiJobId={}", resp != null ? resp.getJobId() : null);
        return resp;
    }

    private static final int MAX_ATTEMPTS = 2;          // 최초 1 + 재시도 1
    private static final long RETRY_BACKOFF_MS = 2000;

    /**
     * AI 서버 일시 오류면 짧은 백오프로 1회 재시도. 타팀 FastAPI가 잠깐 바쁘거나(read 타임아웃),
     * 재시작 중(연결 오류), 5xx(502/503)일 때 두 번째 기회를 준다. 4xx(입력 거부)는 비일시 → 즉시 전파.
     * read 타임아웃은 20s(RestClientConfig)이라 최악 ≈ 20+2+20s로 bounded(무한 대기 없음).
     */
    private AiDto.AiJobInitResponse postWithRetry(String label, Supplier<AiDto.AiJobInitResponse> call) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (RestException e) {
                if (e.getErrorCode() != ErrorCode.AI_SERVER_UNAVAILABLE
                        && e.getErrorCode() != ErrorCode.AI_SERVER_ERROR) {
                    throw e; // 4xx(AI_BAD_INPUT) 등 비일시 — 재시도 무의미
                }
                last = e;
            } catch (ResourceAccessException e) {
                last = e; // I/O(타임아웃·연결) 일시 오류
            }
            if (attempt < MAX_ATTEMPTS) {
                log.warn("[AiApiClient] {} 일시 실패 → {}ms 후 재시도({}/{}). cause={}",
                        label, RETRY_BACKOFF_MS, attempt, MAX_ATTEMPTS, last.toString());
                try {
                    Thread.sleep(RETRY_BACKOFF_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RestException(ErrorCode.AI_SERVER_UNAVAILABLE);
                }
            }
        }
        throw last;
    }

    public AiDto.AiJobStatusResponse getJobStatus(String aiJobId) {
        return restClient.get()
                .uri(aiBaseUrl + "/api/v1/jobs/{jobId}", Map.of("jobId", aiJobId))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(AiDto.AiJobStatusResponse.class);
    }

    private void handleError(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String message = extractMessage(response);

        if (response.getStatusCode().is4xxClientError()) {
            log.warn("AI server rejected input: status={}, uri={}, message={}", status, request.getURI(), message);
            throw new RestException(ErrorCode.AI_BAD_INPUT,
                    message != null ? message : ErrorCode.AI_BAD_INPUT.getMessage());
        }
        if (status == 502 || status == 503) {
            log.error("AI server unavailable: status={}, uri={}", status, request.getURI());
            throw new RestException(ErrorCode.AI_SERVER_UNAVAILABLE);
        }
        log.error("AI server error: status={}, uri={}, message={}", status, request.getURI(), message);
        throw new RestException(ErrorCode.AI_SERVER_ERROR,
                message != null ? message : ErrorCode.AI_SERVER_ERROR.getMessage());
    }

    @SuppressWarnings("unchecked")
    private String extractMessage(ClientHttpResponse response) {
        try {
            String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> map = objectMapper.readValue(body, Map.class);
            Object msg = map.get("message");
            return msg != null ? msg.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
