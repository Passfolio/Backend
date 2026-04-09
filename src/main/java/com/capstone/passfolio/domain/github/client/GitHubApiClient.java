package com.capstone.passfolio.domain.github.client;

import com.capstone.passfolio.domain.github.dto.GitHubDto;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubApiClient {

    private final RestClient restClient;

    private static final String BASE_URL = "https://api.github.com";
    private static final int PER_PAGE = 6;

    public GitHubDto.ApiProfile fetchProfile(String accessToken) {
        return restClient.get()
                .uri(BASE_URL + "/user")
                .headers(h -> applyHeaders(h, accessToken))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(GitHubDto.ApiProfile.class);
    }

    /**
     * 본인 소유 개인 repo만 조회.
     * affiliation=owner 로 org repo를 명시적으로 제외한다.
     * <p>
     * ResponseEntity 로 반환하여 호출자가 {@code Link} 응답 헤더를 통해
     * 다음 페이지 존재 여부를 정확히 판단할 수 있도록 한다.
     * (item 개수 == per_page 비교 방식은 총 개수가 per_page 배수일 때 오판함)
     */
    public ResponseEntity<List<GitHubDto.ApiRepo>> fetchPersonalRepos(String accessToken, String visibility, int page) {
        return restClient.get()
                .uri(BASE_URL + "/user/repos?visibility={v}&affiliation=owner&per_page={pp}&page={p}&sort=updated",
                        visibility, PER_PAGE, page)
                .headers(h -> applyHeaders(h, accessToken))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .toEntity(new ParameterizedTypeReference<List<GitHubDto.ApiRepo>>() {});
    }

    /**
     * GraphQL로 viewer가 실제 기여(commit/issue/PR/PR review)한 repo 목록을 조회한다.
     * REST N+1 호출(org repo 목록 + per-repo contributor 검증)을 단일 요청으로 대체한다.
     *
     * @param accessToken GitHub OAuth access token
     * @param after       GraphQL 커서 (null이면 첫 페이지)
     */
    public GitHubDto.ContributedReposResponse fetchContributedReposGraphQL(String accessToken, String after) {
        String query =
                "query($after: String) {" +
                "  viewer {" +
                "    repositoriesContributedTo(" +
                "      first: 100" +
                "      after: $after" +
                "      contributionTypes: [COMMIT, ISSUE, PULL_REQUEST, PULL_REQUEST_REVIEW]" +
                "    ) {" +
                "      pageInfo { hasNextPage endCursor }" +
                "      nodes {" +
                "        nameWithOwner isPrivate description" +
                "        primaryLanguage { name }" +
                "        owner { login __typename }" +
                "      }" +
                "    }" +
                "  }" +
                "}";

        Map<String, Object> variables = new HashMap<>();
        variables.put("after", after);
        Map<String, Object> body = Map.of("query", query, "variables", variables);

        return restClient.post()
                .uri("https://api.github.com/graphql")
                .headers(h -> applyHeaders(h, accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(GitHubDto.ContributedReposResponse.class);
    }

    private void applyHeaders(org.springframework.http.HttpHeaders headers, String accessToken) {
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
    }

    private void handleError(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        switch (status) {
            case 401 -> throw new RestException(ErrorCode.GITHUB_TOKEN_EXPIRED);
            case 403 -> {
                String remaining = response.getHeaders().getFirst("X-RateLimit-Remaining");
                if ("0".equals(remaining)) {
                    String reset = response.getHeaders().getFirst("X-RateLimit-Reset");
                    log.warn("GitHub rate limit exceeded. Reset at epoch: {}", reset);
                    throw new RestException(ErrorCode.GITHUB_RATE_LIMITED);
                }
                throw new RestException(ErrorCode.GITHUB_API_ERROR, "GitHub API 접근이 거부되었습니다.");
            }
            default -> {
                log.error("GitHub API error: status={}, uri={}", status, request.getURI());
                throw new RestException(ErrorCode.GITHUB_API_ERROR);
            }
        }
    }
}
