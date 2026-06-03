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
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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

    /** 잘못된거 -> 배치 분석 결과(전원 성공) → 포트폴리오 생성 요청(FastAPI). */
    public AiDto.AiJobInitResponse requestPortfolioFromAnalyses(AiDto.AnalysisResultsRequest request) {
        return restClient.post()
                .uri(aiBaseUrl + "/api/v1/portfolio/from-analyses")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(AiDto.AiJobInitResponse.class);
    }

    // 포트폴리오를 업그레이드한다.
    public AiDto.AiJobInitResponse upgradePortfolio(String portfolioPdfUrl, Long userId) {
        return restClient.post()
                .uri(aiBaseUrl + "/api/v1/portfolio/from-pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AiDto.AiPdfRequest(portfolioPdfUrl, userId))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(AiDto.AiJobInitResponse.class);
    }

    // 자소서로 자소서를 업그레이드한다..
    public AiDto.AiJobInitResponse upgradeCoverLetter(String coverLetterPdfUrl, Long userId) {
        return restClient.post()
                .uri(aiBaseUrl + "/api/v1/cover-letter/from-pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AiDto.AiPdfRequest(coverLetterPdfUrl, userId))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(AiDto.AiJobInitResponse.class);
    }

    // 포트폴리오로 자소서를 생성한다.
    public AiDto.AiJobInitResponse generateCoverLetterFromPortfolio(AiDto.AiCoverLetterRequest request) {
        return restClient.post()
                .uri(aiBaseUrl + "/api/v1/cover-letter/from-portfolio")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(AiDto.AiJobInitResponse.class);
    }

    // 자소서로 포트폴리오를 생성한다.
    public AiDto.AiJobInitResponse generatePortfolioFromCoverLetter(AiDto.AiCoverLetterRequest request) {
        return restClient.post()
                .uri(aiBaseUrl + "/api/v1/portfolio/from-cover-letter")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(AiDto.AiJobInitResponse.class);
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
