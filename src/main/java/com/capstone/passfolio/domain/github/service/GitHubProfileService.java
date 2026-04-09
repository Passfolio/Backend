package com.capstone.passfolio.domain.github.service;

import com.capstone.passfolio.domain.github.client.GitHubApiClient;
import com.capstone.passfolio.domain.github.dto.GitHubDto;
import com.capstone.passfolio.domain.github.repository.GitHubTokenRedisRepository;
import com.capstone.passfolio.domain.github.util.GitHubCursorUtils;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.system.config.encryption.AesEncryptor;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubProfileService {

    private final GitHubTokenRedisRepository tokenRedisRepository;
    private final GitHubApiClient gitHubApiClient;
    private final AesEncryptor aesEncryptor;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    private static final int PER_PAGE = 6;

    public GitHubDto.ProfileResponse getProfile(Long userId) {
        Optional<String> cached = tokenRedisRepository.getCachedProfile(userId);
        if (cached.isPresent()) {
            GitHubDto.ProfileResponse hit = deserialize(cached.get(), GitHubDto.ProfileResponse.class);
            if (hit != null) return hit;
        }

        try {
            String token = resolveAccessToken(userId);
            GitHubDto.ApiProfile api = gitHubApiClient.fetchProfile(token);
            GitHubDto.ProfileResponse response = GitHubDto.ProfileResponse.builder()
                    .login(api.getLogin())
                    .name(api.getName())
                    .avatarUrl(api.getAvatarUrl())
                    .build();
            tokenRedisRepository.cacheProfile(userId, serialize(response));
            return response;
        } catch (RestException e) {
            if (e.getErrorCode() == ErrorCode.GITHUB_TOKEN_NOT_FOUND
                    || e.getErrorCode() == ErrorCode.GITHUB_TOKEN_EXPIRED) {
                throw e;
            }
            log.warn("GitHub API failed for profile, falling back to DB. userId={}", userId, e);
            return fallbackProfileFromDb(userId);
        }
    }

    public GitHubDto.RepoListResponse getPublicRepos(Long userId, String cursor) {
        return getPersonalRepos(userId, "public", cursor);
    }

    public GitHubDto.RepoListResponse getPrivateRepos(Long userId, String cursor) {
        return getPersonalRepos(userId, "private", cursor);
    }

    /**
     * GraphQL {@code repositoriesContributedTo}로 viewer가 실제 기여한 org repo 목록을
     * 6개 단위 페이지네이션으로 반환한다.
     * <p>
     * cursor 내부에 GraphQL endCursor(c)와 청크 내 offset(o)을 함께 인코딩하여
     * GraphQL 100개 청크를 6개 단위로 재분할한다.
     * <ul>
     *   <li>현재 청크에 남은 org repo가 6개 이상 → offset만 증가, 같은 GraphQL 청크 재사용</li>
     *   <li>현재 청크 소진 + GraphQL 다음 페이지 존재 → 다음 GraphQL 청크로 이동, offset 리셋</li>
     *   <li>현재 청크 소진 + GraphQL 마지막 페이지 → nextCursor = null</li>
     * </ul>
     */
    public GitHubDto.RepoListResponse getOrgRepos(Long userId, String cursor) {
        var orgCursor = GitHubCursorUtils.decodeOrgCursor(cursor);
        String cacheKey = "organization:" + requireNonNullElse(orgCursor.after(), "") + ":" + orgCursor.offset();

        Optional<String> cached = tokenRedisRepository.getCachedRepos(userId, cacheKey, 0);
        if (cached.isPresent()) {
            GitHubDto.RepoListResponse hit = deserialize(cached.get(), GitHubDto.RepoListResponse.class);
            if (hit != null) return hit;
        }

        String token = resolveAccessToken(userId);

        GitHubDto.ContributedReposResponse graphqlResponse =
                gitHubApiClient.fetchContributedReposGraphQL(token, orgCursor.after());

        var contributedTo = graphqlResponse.getData().getViewer().getRepositoriesContributedTo();
        boolean graphqlHasNextPage = contributedTo.getPageInfo().isHasNextPage();
        String graphqlEndCursor = contributedTo.getPageInfo().getEndCursor();

        // 현재 GraphQL 청크에서 org repo 전체 추출
        List<GitHubDto.RepoItem> allOrgInChunk = requireNonNullElse(
                        contributedTo.getNodes(),
                        List.<GitHubDto.ContributedReposResponse.RepoNode>of())
                .stream()
                .filter(node -> "Organization".equals(node.getOwner().getTypename()))
                .map(node -> {
                    String repoName = node.getNameWithOwner().contains("/")
                            ? node.getNameWithOwner().split("/", 2)[1]
                            : node.getNameWithOwner();
                    String language = node.getPrimaryLanguage() != null
                            ? node.getPrimaryLanguage().getName()
                            : null;
                    return GitHubDto.RepoItem.builder()
                            .name(repoName)
                            .description(node.getDescription())
                            .language(language)
                            .build();
                })
                .toList();

        // offset 적용 후 PER_PAGE 만큼 잘라서 반환
        int offset = orgCursor.offset();
        List<GitHubDto.RepoItem> pageRepos = allOrgInChunk.stream()
                .skip(offset)
                .limit(PER_PAGE)
                .toList();

        // 다음 커서 결정
        int nextOffset = offset + PER_PAGE;
        String nextCursor;
        if (nextOffset < allOrgInChunk.size()) {
            // 현재 GraphQL 청크에 org repo가 더 있음 → offset만 증가
            nextCursor = GitHubCursorUtils.encodeOrgCursor(orgCursor.after(), nextOffset, true);
        } else if (graphqlHasNextPage) {
            // 현재 청크 소진, 다음 GraphQL 페이지로 이동 → offset 리셋
            nextCursor = GitHubCursorUtils.encodeOrgCursor(graphqlEndCursor, 0, true);
        } else {
            nextCursor = null;
        }

        GitHubDto.RepoListResponse response = GitHubDto.RepoListResponse.builder()
                .type("organization")
                .perPage(pageRepos.size())
                .nextCursor(nextCursor)
                .repos(pageRepos)
                .build();

        tokenRedisRepository.cacheRepos(userId, cacheKey, 0, serialize(response));
        return response;
    }

    // affiliation=owner 로 본인 소유 repo만 조회 (org repo 제외)
    private GitHubDto.RepoListResponse getPersonalRepos(Long userId, String visibility, String cursor) {
        int page = GitHubCursorUtils.decodePage(cursor);

        Optional<String> cached = tokenRedisRepository.getCachedRepos(userId, visibility, page);
        if (cached.isPresent()) {
            GitHubDto.RepoListResponse hit = deserialize(cached.get(), GitHubDto.RepoListResponse.class);
            if (hit != null) return hit;
        }

        String token = resolveAccessToken(userId);
        ResponseEntity<List<GitHubDto.ApiRepo>> entity = gitHubApiClient.fetchPersonalRepos(token, visibility, page);
        List<GitHubDto.ApiRepo> apiRepos = requireNonNullElse(entity.getBody(), List.of());
        List<GitHubDto.RepoItem> repos = toRepoItems(apiRepos);

        // GitHub Link 헤더로 다음 페이지 존재 여부 판단.
        // item 개수 == PER_PAGE 비교는 총 개수가 PER_PAGE 배수일 때 빈 페이지를 오판하므로 사용하지 않는다.
        boolean hasMore = hasNextPage(entity.getHeaders().getFirst("Link"));
        GitHubDto.RepoListResponse response = GitHubDto.RepoListResponse.builder()
                .type(visibility)
                .perPage(PER_PAGE)
                .nextCursor(GitHubCursorUtils.encodeCursor(page + 1, hasMore))
                .repos(repos)
                .build();

        tokenRedisRepository.cacheRepos(userId, visibility, page, serialize(response));
        return response;
    }

    /**
     * GitHub REST API {@code Link} 응답 헤더에 {@code rel="next"} 가 포함되면 다음 페이지가 존재한다.
     * 헤더가 없거나 next 링크가 없으면 현재 페이지가 마지막이다.
     */
    private boolean hasNextPage(String linkHeader) {
        return linkHeader != null && linkHeader.contains("rel=\"next\"");
    }

    private List<GitHubDto.RepoItem> toRepoItems(List<GitHubDto.ApiRepo> apiRepos) {
        return apiRepos.stream()
                .map(r -> GitHubDto.RepoItem.builder()
                        .name(r.getName())
                        .description(r.getDescription())
                        .language(r.getLanguage())
                        .build())
                .toList();
    }

    private String resolveAccessToken(Long userId) {
        String encrypted = tokenRedisRepository.getAccessToken(userId)
                .orElseThrow(() -> new RestException(ErrorCode.GITHUB_TOKEN_NOT_FOUND));
        try {
            return aesEncryptor.decrypt(encrypted);
        } catch (Exception e) {
            log.error("Failed to decrypt GitHub token. userId={}", userId, e);
            tokenRedisRepository.deleteAccessToken(userId);
            throw new RestException(ErrorCode.GITHUB_TOKEN_NOT_FOUND);
        }
    }

    private GitHubDto.ProfileResponse fallbackProfileFromDb(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));
        return GitHubDto.ProfileResponse.builder()
                .login(user.getGithubLogin())
                .name(user.getNickname())
                .avatarUrl(user.getProfileImageUrl())
                .build();
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RestException(ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR, e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Failed to deserialize cached data: {}", e.getMessage());
            return null;
        }
    }
}
