package com.capstone.passfolio.domain.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GitHubApiProfileDto {
    private Long id;
    private String login;
    private String name;
    @JsonProperty("avatar_url")
    private String avatarUrl;
}
