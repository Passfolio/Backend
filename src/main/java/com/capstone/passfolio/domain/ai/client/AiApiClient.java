package com.capstone.passfolio.domain.ai.client;

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
import java.util.Map;

@Slf4j
@Component
public class AiApiClient {

    private final RestClient restClient;
    private final String aiBaseUrl;

    public AiApiClient(
            @Qualifier(RestClientConfig.AI_REST_CLIENT) RestClient restClient,
            @Value("${ai.base-url}") String aiBaseUrl) {
        this.restClient = restClient;
        this.aiBaseUrl = aiBaseUrl;
    }

    public AiDto.AiJobInitResponse requestPortfolioFromPdf(String pdfUrl, Long userId) {
        return restClient.post()
                .uri(aiBaseUrl + "/api/v1/portfolio/from-pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AiDto.AiPdfRequest(pdfUrl, userId))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(AiDto.AiJobInitResponse.class);
    }

    public AiDto.AiJobInitResponse requestCoverLetterFromPdf(String pdfUrl, Long userId) {
        return restClient.post()
                .uri(aiBaseUrl + "/api/v1/cover-letter/from-pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AiDto.AiPdfRequest(pdfUrl, userId))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(AiDto.AiJobInitResponse.class);
    }

    public AiDto.AiJobInitResponse requestCoverLetterFromPortfolio(AiDto.AiCoverLetterRequest request) {
        return restClient.post()
                .uri(aiBaseUrl + "/api/v1/cover-letter/from-portfolio")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(AiDto.AiJobInitResponse.class);
    }

    public AiDto.AiJobInitResponse requestPortfolioFromCoverLetter(AiDto.AiCoverLetterRequest request) {
        return restClient.post()
                .uri(aiBaseUrl + "/api/v1/cover-letter/to-portfolio")
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
        if (status == 503 || status == 502) {
            log.error("AI server unavailable: status={}, uri={}", status, request.getURI());
            throw new RestException(ErrorCode.AI_SERVER_UNAVAILABLE);
        }
        log.error("AI server error: status={}, uri={}", status, request.getURI());
        throw new RestException(ErrorCode.AI_SERVER_ERROR);
    }
}
