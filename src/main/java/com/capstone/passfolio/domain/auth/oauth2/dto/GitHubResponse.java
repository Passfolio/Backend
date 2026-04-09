package com.capstone.passfolio.domain.auth.oauth2.dto;

import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class GitHubResponse implements OAuth2Response {
    private final Map<String, Object> attributes;

    @Override public String getProvider() { return "github"; }

    @Override public String getProviderId() {
        Object id = attributes.get("id");
        return (id == null) ? null : String.valueOf(id);
    }

    @Override public String getNickname() {
        Object name = attributes.get("name");
        if (name != null && !name.toString().isBlank()) return name.toString();
        Object login = attributes.get("login");
        return (login == null) ? null : login.toString();
    }

    @Override public String getProfileImageUrl() {
        Object avatarUrl = attributes.get("avatar_url");
        return (avatarUrl == null) ? null : avatarUrl.toString();
    }

    public String getLogin() {
        Object login = attributes.get("login");
        return (login == null) ? null : login.toString();
    }

    public Long getGithubNumericId() {
        Object id = attributes.get("id");
        if (id == null) return null;
        try {
            return Long.valueOf(id.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

