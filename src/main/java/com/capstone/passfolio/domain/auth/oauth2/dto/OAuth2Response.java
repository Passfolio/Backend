package com.capstone.passfolio.domain.auth.oauth2.dto;

public interface OAuth2Response {
    String getProvider();
    String getProviderId();
    String getNickname();
    default String getProfileImageUrl() { return null; }
}