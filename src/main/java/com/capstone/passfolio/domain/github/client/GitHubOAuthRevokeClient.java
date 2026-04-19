package com.capstone.passfolio.domain.github.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class GitHubOAuthRevokeClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public GitHubOAuthRevokeClient(
            RestClient restClient,
            @Value("${spring.security.oauth2.client.registration.github.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.github.client-secret}") String clientSecret) {
        this.restClient = restClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * GitHub OAuth app grant revoke — best-effort.
     * 실패해도 호출자가 로그아웃을 중단하지 않도록 예외를 삼킨다.
     */
    public void revokeGrant(String githubAccessToken) {
        String credentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());
        try {
            restClient.method(HttpMethod.DELETE)
                    .uri("https://api.github.com/applications/{clientId}/grant", clientId)
                    .header("Authorization", "Basic " + credentials)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("access_token", githubAccessToken))
                    .retrieve()
                    .toBodilessEntity();
            log.info("✅ GitHub OAuth grant revoked");
        } catch (Exception e) {
            log.warn("⚠️ GitHub OAuth grant revoke failed (best-effort) — {}", e.getMessage());
        }
    }
}
