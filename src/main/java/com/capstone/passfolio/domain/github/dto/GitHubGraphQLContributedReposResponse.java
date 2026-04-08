package com.capstone.passfolio.domain.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class GitHubGraphQLContributedReposResponse {

    private DataWrapper data;

    @Data
    public static class DataWrapper {
        private ViewerData viewer;
    }

    @Data
    public static class ViewerData {
        private ReposContributedTo repositoriesContributedTo;
    }

    @Data
    public static class ReposContributedTo {
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
