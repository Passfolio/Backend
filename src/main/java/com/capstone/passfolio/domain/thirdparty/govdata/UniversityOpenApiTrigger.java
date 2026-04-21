package com.capstone.passfolio.domain.thirdparty.govdata;

import com.capstone.passfolio.system.config.http.RestClientConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class UniversityOpenApiTrigger {

    private static final int DEFAULT_MAX_RETRY = 5;
    private static final ZoneId UTC = ZoneId.of("UTC");

    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 40000;

    private static final JsonMapper LENIENT_JSON = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .build();

    private final RestClient govDataRestClient;
    private final String baseUrl;
    private final String serviceKey;

    public UniversityOpenApiTrigger(
            @Qualifier(RestClientConfig.GOV_DATA_REST_CLIENT) RestClient baseClient,
            @Value("${app.gov-data.univ-major.base-url}") String baseUrl,
            @Value("${app.gov-data.univ-major.service-key}") String serviceKey
    ) {
        this.govDataRestClient = createTimeoutClient(baseClient);
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
    }

    /**
     * timeout 적용 RestClient 생성
     */
    private RestClient createTimeoutClient(RestClient baseClient) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public Optional<JsonNode> callApi(LinkedHashMap<String, String> queryParams) {
        return callApi(queryParams, DEFAULT_MAX_RETRY);
    }

    public Optional<JsonNode> callApi(LinkedHashMap<String, String> queryParams, int maxRetry) {
        requireServiceKey();

        int backoffSeconds = 1;

        for (int attempt = 0; attempt < maxRetry; attempt++) {
            try {
                URI uri = buildUri(queryParams);

                String body = govDataRestClient.get()
                        .uri(uri)
                        .retrieve()
                        .body(String.class);

                JsonNode root = parseJsonWithRetry(body, attempt, maxRetry, backoffSeconds);

                JsonNode header = root.path("response").path("header");
                String resultCode = header.path("resultCode").asText("");

                if ("00".equals(resultCode)) {
                    return Optional.of(root);
                }

                if ("03".equals(resultCode)) {
                    return Optional.empty();
                }

                if ("22".equals(resultCode)) {
                    log.warn("API 제한 → backoff {}s", backoffSeconds);
                    sleepWithJitter(backoffSeconds);
                    backoffSeconds *= 2;
                    continue;
                }

                throw new IllegalStateException("API ERROR: " + header);

            } catch (ResourceAccessException e) {
                handleNetworkException(e, attempt, maxRetry);

                sleepWithJitter(backoffSeconds);
                backoffSeconds *= 2;

            } catch (RestClientResponseException e) {
                throw new IllegalStateException("HTTP ERROR: " + e.getStatusCode(), e);
            }
        }

        throw new IllegalStateException("API CALL FAILED");
    }

    /**
     * JSON 파싱 retry
     */
    private JsonNode parseJsonWithRetry(String body, int attempt, int maxRetry, int backoffSeconds) {
        try {
            return LENIENT_JSON.readTree(body);
        } catch (JsonProcessingException e) {
            log.warn("JSON 파싱 실패 → retry {}", attempt + 1);

            if (attempt == maxRetry - 1) {
                throw new IllegalStateException("JSON 파싱 실패", e);
            }

            sleepWithJitter(backoffSeconds);
            return null;
        }
    }

    /**
     * 네트워크 에러 타입 분리
     */
    private void handleNetworkException(ResourceAccessException e, int attempt, int maxRetry) {
        Throwable cause = e.getCause();

        if (cause instanceof SocketTimeoutException) {
            log.warn("[ReadTimeout] retry={}", attempt + 1);
        } else if (cause instanceof ConnectException) {
            log.warn("[ConnectError] retry={}", attempt + 1);
        } else {
            log.warn("[Unknown Network Error] retry={} cause={}", attempt + 1, e.getMessage());
        }

        if (attempt == maxRetry - 1) {
            throw e;
        }
    }

    /**
     * jitter 포함 sleep
     */
    private static void sleepWithJitter(int baseSeconds) {
        int jitterMillis = ThreadLocalRandom.current().nextInt(500, 1500);
        long sleepMillis = baseSeconds * 1000L + jitterMillis;

        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public UniversityOpenApiProbeResult probeUnivMajorOpenApi() {
        return probeUnivMajorOpenApi(DEFAULT_MAX_RETRY);
    }

    public UniversityOpenApiProbeResult probeUnivMajorOpenApi(int maxRetry) {
        requireServiceKey();

        int currentYear = LocalDate.now(UTC).getYear();

        for (int i = 0; i < maxRetry; i++) {
            String yr = String.valueOf(currentYear - i);

            LinkedHashMap<String, String> params = new LinkedHashMap<>();
            params.put("serviceKey", serviceKey);
            params.put("pageNo", "1");
            params.put("numOfRows", "1");
            params.put("type", "json");
            params.put("YR", yr);

            Optional<JsonNode> result = callApi(params, maxRetry);

            if (result.isEmpty()) {
                log.warn("[probe] YR={} : NO_DATA", yr);
                continue;
            }

            JsonNode root = result.get();
            int totalCount = parseTotalCount(
                    root.path("response").path("body").path("totalCount")
            );

            log.info("[probe] API OK — YR={}, totalCount={}", yr, totalCount);

            return new UniversityOpenApiProbeResult(yr, totalCount);
        }

        throw new IllegalStateException("YR 탐색 실패");
    }

    public record UniversityOpenApiProbeResult(String yr, int totalCount) {}

    private void requireServiceKey() {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException("SERVICE KEY missing");
        }
    }

    private URI buildUri(LinkedHashMap<String, String> queryParams) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(baseUrl);
        queryParams.forEach(b::queryParam);
        return b.build().encode().toUri();
    }

    private static int parseTotalCount(JsonNode node) {
        if (node == null || node.isNull()) return 0;

        try {
            return Integer.parseInt(node.asText("0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}