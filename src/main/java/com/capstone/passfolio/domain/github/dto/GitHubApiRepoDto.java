package com.capstone.passfolio.domain.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GitHubApiRepoDto {
    private String name;
    private String description;
    private String language;
    @JsonProperty("private")
    private boolean privateRepo;
    @JsonProperty("html_url")
    private String htmlUrl;
    @JsonProperty("full_name")
    private String fullName; // "{owner}/{repo}" 형식
}
