package com.capstone.passfolio.domain.github.controller;

import com.capstone.passfolio.domain.github.dto.GitHubProfileResponse;
import com.capstone.passfolio.domain.github.dto.GitHubRepoListResponse;
import com.capstone.passfolio.domain.github.service.GitHubProfileService;
import com.capstone.passfolio.system.exception.dto.ErrorResponse;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/github")
@Tag(
    name = "GitHub",
    description = """
        GitHub 프로필 및 저장소 조회 API

        - GitHub OAuth2 로그인 후 발급된 세션(JWT)이 필요합니다.
        - GitHub 액세스 토큰은 서버 Redis에 암호화 저장되며, 클라이언트가 직접 전달하지 않아도 됩니다.
        - 저장소 목록은 커서 기반 페이지네이션을 사용합니다.
        - 응답 데이터는 Redis에 5분간 캐싱됩니다.
        """
)
@SecurityRequirement(name = "bearerAuth")
public class GitHubController {

    private final GitHubProfileService gitHubProfileService;

    @Operation(
        summary = "GitHub 프로필 조회",
        description = """
            로그인한 사용자의 GitHub 프로필을 반환합니다.

            ## 사전 조건
            - GitHub OAuth2로 로그인한 사용자만 접근 가능합니다.
            - GitHub 액세스 토큰이 Redis에 존재해야 합니다.

            ## 처리 흐름
            1. Redis에서 프로필 캐시 조회 (캐시 TTL: 10분)
            2. 캐시 미스 시 GitHub API `/user` 호출
            3. GitHub API 실패 시 DB(users 테이블)의 저장값으로 fallback 반환

            ## 주의사항
            - GitHub 토큰 만료(`GITHUB_TOKEN_EXPIRED`) 시 GitHub으로 재로그인이 필요합니다.
            - fallback 시 반환되는 `name`은 DB에 저장된 nickname입니다.
            """
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "프로필 조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GitHubProfileResponse.class),
                examples = @ExampleObject(
                    name = "성공 응답",
                    value = """
                        {
                          "login": "hooby3dfx",
                          "name": "Hooby Park",
                          "avatarUrl": "https://avatars.githubusercontent.com/u/12345678?v=4"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = """
                인증 실패

                | ErrorCode | 상황 | 해결 방법 |
                |-----------|------|----------|
                | `JWT_INVALID` | 유효하지 않은 JWT | 재로그인 |
                | `JWT_EXPIRED` | 만료된 JWT | 토큰 갱신 또는 재로그인 |
                | `GITHUB_TOKEN_NOT_FOUND` | Redis에 GitHub 토큰 없음 | GitHub OAuth2 재로그인 |
                | `GITHUB_TOKEN_EXPIRED` | GitHub 액세스 토큰 만료 | GitHub OAuth2 재로그인 |
                """,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    name = "GitHub 토큰 만료",
                    value = """
                        {
                          "status": "UNAUTHORIZED",
                          "error": "GITHUB_TOKEN_EXPIRED",
                          "message": "GitHub 토큰이 만료되었습니다. GitHub으로 다시 로그인해주세요."
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "429",
            description = """
                요청 한도 초과

                | ErrorCode | 상황 | 해결 방법 |
                |-----------|------|----------|
                | `GITHUB_RATE_LIMITED` | GitHub API Rate Limit 초과 (인증 기준 5,000req/h) | X-RateLimit-Reset 시각 이후 재시도 |
                """,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "502",
            description = """
                GitHub API 오류

                | ErrorCode | 상황 |
                |-----------|------|
                | `GITHUB_API_ERROR` | GitHub API 서버 오류 또는 예기치 않은 응답 |
                """,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @GetMapping("/profile")
    public GitHubProfileResponse getProfile(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return gitHubProfileService.getProfile(userPrincipal.getUserId());
    }

    @Operation(
        summary = "GitHub 저장소 목록 조회",
        description = """
            로그인한 사용자의 GitHub 저장소 목록을 커서 기반 페이지네이션으로 반환합니다.

            ## 저장소 타입

            | type | 설명 | 데이터 소스 |
            |------|------|------------|
            | `public` | 본인 소유 공개 저장소 | REST API (`affiliation=owner`) |
            | `private` | 본인 소유 비공개 저장소 | REST API (`affiliation=owner`) |
            | `organization` | 소속 조직에서 실제 기여한 저장소 | **GraphQL** `repositoriesContributedTo` |

            ## organization 타입 특이사항
            - COMMIT, ISSUE, PR, PR REVIEW 중 하나라도 기여 이력이 있는 org 저장소만 포함됩니다.
            - 단일 GraphQL 호출(최대 100개 청크)로 org 필터링 후 6개 단위로 재분할하여 반환합니다.
            - cursor 내부에 GraphQL endCursor와 청크 내 offset을 함께 인코딩합니다.

            ## 커서 기반 페이지네이션
            - 첫 요청: `cursor` 파라미터 생략
            - 다음 페이지: 이전 응답의 `nextCursor` 값을 `cursor`에 전달
            - `nextCursor`가 `null`이면 마지막 페이지입니다.
            - cursor는 서버 내부 포맷으로 인코딩된 불투명(opaque) 토큰이므로 직접 파싱하지 마세요.

            ## 사전 조건
            - GitHub OAuth2로 로그인한 사용자만 접근 가능합니다.
            - `private` 타입은 GitHub OAuth2 scope에 `repo` 권한이 있어야 합니다.

            ## 연관 API
            - `GET /api/v1/github/profile` - GitHub 프로필 조회
            """
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "저장소 목록 조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GitHubRepoListResponse.class),
                examples = {
                    @ExampleObject(
                        name = "public/private 응답 (6개 단위 페이지네이션)",
                        value = """
                            {
                              "type": "public",
                              "perPage": 6,
                              "nextCursor": "eyJwIjoyfQ",
                              "repos": [
                                {
                                  "name": "my-project",
                                  "description": "개인 프로젝트 설명",
                                  "language": "Java"
                                },
                                {
                                  "name": "portfolio-site",
                                  "description": null,
                                  "language": "TypeScript"
                                }
                              ]
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "organization 응답 (GraphQL 필터링 후 실제 수)",
                        value = """
                            {
                              "type": "organization",
                              "perPage": 3,
                              "nextCursor": null,
                              "repos": [
                                {
                                  "name": "backend-service",
                                  "description": "팀 백엔드 서버",
                                  "language": "Java"
                                },
                                {
                                  "name": "frontend-app",
                                  "description": "팀 프론트엔드",
                                  "language": "TypeScript"
                                },
                                {
                                  "name": "infra-scripts",
                                  "description": null,
                                  "language": "Shell"
                                }
                              ]
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "마지막 페이지 응답",
                        value = """
                            {
                              "type": "public",
                              "perPage": 2,
                              "nextCursor": null,
                              "repos": [
                                {
                                  "name": "old-repo",
                                  "description": "오래된 저장소",
                                  "language": "Python"
                                }
                              ]
                            }
                            """
                    )
                }
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = """
                잘못된 요청

                | ErrorCode | 상황 | 해결 방법 |
                |-----------|------|----------|
                | `GLOBAL_BAD_REQUEST` | `type` 값이 public/private/organization 이외의 값 | 올바른 type 값 사용 |
                | `GLOBAL_INVALID_PARAMETER` | `type` 파라미터 누락 | 필수 파라미터 포함 |
                """,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    name = "잘못된 type 값",
                    value = """
                        {
                          "status": "BAD_REQUEST",
                          "error": "GLOBAL_BAD_REQUEST",
                          "message": "type은 public, private, organization 중 하나여야 합니다."
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = """
                인증 실패

                | ErrorCode | 상황 | 해결 방법 |
                |-----------|------|----------|
                | `JWT_INVALID` | 유효하지 않은 JWT | 재로그인 |
                | `JWT_EXPIRED` | 만료된 JWT | 토큰 갱신 또는 재로그인 |
                | `GITHUB_TOKEN_NOT_FOUND` | Redis에 GitHub 토큰 없음 | GitHub OAuth2 재로그인 |
                | `GITHUB_TOKEN_EXPIRED` | GitHub 액세스 토큰 만료 | GitHub OAuth2 재로그인 |
                """,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "429",
            description = """
                요청 한도 초과

                | ErrorCode | 상황 | 해결 방법 |
                |-----------|------|----------|
                | `GITHUB_RATE_LIMITED` | GitHub API Rate Limit 초과 | 잠시 후 재시도 |
                """,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "502",
            description = """
                GitHub API 오류

                | ErrorCode | 상황 |
                |-----------|------|
                | `GITHUB_API_ERROR` | GitHub API 서버 오류 |
                """,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @GetMapping("/repos")
    public GitHubRepoListResponse getRepos(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal userPrincipal,

            @Parameter(
                name = "type",
                description = """
                    조회할 저장소 타입
                    - `public`: 본인 소유 공개 저장소
                    - `private`: 본인 소유 비공개 저장소
                    - `organization`: 소속 조직에서 실제 기여한 저장소 (commit/issue/PR/PR review 기준)
                    """,
                required = true,
                example = "public",
                schema = @Schema(allowableValues = {"public", "private", "organization"})
            )
            @RequestParam String type,

            @Parameter(
                name = "cursor",
                description = """
                    페이지네이션 커서 (opaque token)
                    - 첫 요청 시 생략
                    - 이후 요청 시 이전 응답의 `nextCursor` 값을 그대로 전달
                    - `nextCursor`가 null이었다면 더 이상 페이지가 없습니다.
                    """,
                required = false,
                example = "eyJwIjoyfQ"
            )
            @RequestParam(required = false) String cursor) {
        return switch (type) {
            case "public"       -> gitHubProfileService.getPublicRepos(userPrincipal.getUserId(), cursor);
            case "private"      -> gitHubProfileService.getPrivateRepos(userPrincipal.getUserId(), cursor);
            case "organization" -> gitHubProfileService.getOrgRepos(userPrincipal.getUserId(), cursor);
            default -> throw new RestException(ErrorCode.GLOBAL_BAD_REQUEST,
                    "type은 public, private, organization 중 하나여야 합니다.");
        };
    }
}
