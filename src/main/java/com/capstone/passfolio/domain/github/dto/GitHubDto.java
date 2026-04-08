package com.capstone.passfolio.domain.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

public class GitHubDto {

    private GitHubDto() {}

    // ── Client-facing responses ───────────────────────────────────────────

    @Builder
    @Data
    @Schema(description = "GitHub 프로필 응답")
    public static class ProfileResponse {

        @Schema(description = "GitHub 로그인 ID (username)", example = "hooby3dfx")
        private String login;

        @Schema(
            description = "GitHub 표시 이름. GitHub API 실패 시 DB에 저장된 nickname으로 fallback됩니다.",
            example = "Hooby Park",
            nullable = true
        )
        private String name;

        @Schema(
            description = "GitHub 프로필 이미지 URL",
            example = "https://avatars.githubusercontent.com/u/12345678?v=4",
            nullable = true
        )
        private String avatarUrl;
    }

    @Builder
    @Data
    @Schema(description = "GitHub 저장소 목록 응답")
    public static class RepoListResponse {

        @Schema(
            description = "조회한 저장소 타입",
            example = "public",
            allowableValues = {"public", "private", "organization"}
        )
        private String type;

        @Schema(
            description = """
                이번 응답에 포함된 저장소 수.
                public/private: 최대 6개.
                organization: GraphQL 청크 필터링 후 실제 수 (마지막 페이지는 6개 미만 가능).
                """,
            example = "6"
        )
        private int perPage;

        @Schema(
            description = """
                다음 페이지 커서 (opaque Base64Url 토큰).
                다음 요청의 cursor 파라미터로 그대로 전달하세요.
                null이면 마지막 페이지입니다.
                """,
            example = "eyJwIjoyfQ",
            nullable = true
        )
        private String nextCursor;

        @Schema(description = "저장소 목록")
        private List<RepoItem> repos;
    }

    @Builder
    @Data
    @Schema(description = "GitHub 저장소 정보")
    public static class RepoItem {

        @Schema(description = "저장소 이름 (owner 제외)", example = "backend-service")
        private String name;

        @Schema(
            description = "저장소 설명. GitHub에 설명이 없으면 null입니다.",
            example = "팀 백엔드 서비스",
            nullable = true
        )
        private String description;

        @Schema(
            description = "주 사용 언어. 코드가 없거나 감지 불가 시 null입니다.",
            example = "Java",
            nullable = true
        )
        private String language;
    }

    // ── GitHub REST API internal DTOs ─────────────────────────────────────

    @Data
    public static class ApiProfile {
        private Long id;
        private String login;
        private String name;
        @JsonProperty("avatar_url")
        private String avatarUrl;
    }

    @Data
    public static class ApiRepo {
        private String name;
        private String description;
        private String language;
        @JsonProperty("private")
        private boolean privateRepo;
        @JsonProperty("html_url")
        private String htmlUrl;
        @JsonProperty("full_name")
        private String fullName;
    }

    // ── GitHub GraphQL internal DTOs ──────────────────────────────────────

    @Data
    public static class ContributedReposResponse {

        private DataWrapper data;

        @Data
        public static class DataWrapper {
            private ViewerData viewer;
        }

        @Data
        public static class ViewerData {
            private ContributedTo repositoriesContributedTo;
        }

        @Data
        public static class ContributedTo {
            private PageInfo pageInfo;
            private List<RepoNode> nodes;
        }

        @Data
        public static class PageInfo {
            private boolean hasNextPage;
            private String endCursor;
        }

        @Data
        public static class RepoNode {
            private String nameWithOwner;
            private boolean isPrivate;
            private String description;
            private PrimaryLanguage primaryLanguage;
            private OwnerInfo owner;
        }

        @Data
        public static class PrimaryLanguage {
            private String name;
        }

        @Data
        public static class OwnerInfo {
            private String login;
            @JsonProperty("__typename")
            private String typename;
        }
    }
}
