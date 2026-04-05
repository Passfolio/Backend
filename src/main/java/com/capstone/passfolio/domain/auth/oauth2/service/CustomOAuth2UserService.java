package com.capstone.passfolio.domain.auth.oauth2.service;

import com.capstone.passfolio.system.util.HmacUtil;
import com.capstone.passfolio.domain.github.repository.GitHubTokenRedisRepository;
import com.capstone.passfolio.domain.auth.oauth2.dto.*;
import com.capstone.passfolio.domain.auth.oauth2.entity.CustomOAuth2User;
import com.capstone.passfolio.domain.auth.oauth2.entity.enums.ProviderType;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.entity.enums.Role;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.system.config.encryption.AesEncryptor;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final UserRepository userRepository;
    private final HmacUtil hmacUtil;
    private final GitHubTokenRedisRepository githubTokenRedisRepository;
    private final AesEncryptor aesEncryptor;

    @Transactional
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        // Request 기반으로 OAuth2User 정의
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // OAuth2User + Request 기반으로 Response 생성
        OAuth2Response oAuth2Response = getOAuth2Response(userRequest, oAuth2User);
        log.info("🟢 OAuth2 User nickname: {}", oAuth2Response.getNickname());

        // Response 할 DTO
        final OAuth2UserDto oAuth2UserDto = OAuth2UserDto.of(Role.USER, oAuth2Response, hmacUtil);

        log.info("Check Whether if the user is already SocialUser");
        // DB에는 해시된 providerId가 저장되어 있으므로, 비교 전에 해시 처리 필요
        String hashedProviderId = hmacUtil.hmacSha256Base64(oAuth2Response.getProviderId());
        ProviderType providerType = ProviderType.from(oAuth2Response.getProvider());

        User user = userRepository.findByProviderIdAndProviderType(
                hashedProviderId,
                providerType
        ).orElse(null);

        if (user != null) {
            log.info("🟢 Find existing OAuth2User");

            // TODO: Origin Profile is not matched with login account info -> update it
            user.updateProfileImageUrl(oAuth2Response.getProfileImageUrl());
            storeGithubAccessToken(userRequest, user.getId());
            return new CustomOAuth2User(OAuth2UserDto.from(user));
        }

        log.info("The same user is not exist. Considering you are new social user");
        user = userRepository.save(oAuth2UserDto.toUser());
        storeGithubAccessToken(userRequest, user.getId());

        return new CustomOAuth2User(OAuth2UserDto.from(user));
    }

    private void storeGithubAccessToken(OAuth2UserRequest userRequest, Long userId) {
        if (!"github".equals(userRequest.getClientRegistration().getRegistrationId()) || userId == null) return;
        try {
            String accessToken = userRequest.getAccessToken().getTokenValue();
            String encryptedToken = aesEncryptor.encrypt(accessToken);
            githubTokenRedisRepository.saveAccessToken(userId, encryptedToken);
            log.info("🟢 GitHub access token stored for userId: {}", userId);
        } catch (Exception e) {
            log.warn("🔴 Failed to store GitHub access token for userId: {}", userId, e);
        }
    }

    private static OAuth2Response getOAuth2Response(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2Response oAuth2Response = null;

        switch (registrationId) {
            case "github" -> oAuth2Response = new GitHubResponse(oAuth2User.getAttributes());
            default -> throw new OAuth2AuthenticationException(ErrorCode.OAUTH_BAD_REQUEST.getMessage());
        }
        return oAuth2Response;
    }
}
