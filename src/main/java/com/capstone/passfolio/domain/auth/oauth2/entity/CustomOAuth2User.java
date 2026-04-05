package com.capstone.passfolio.domain.auth.oauth2.entity;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import com.capstone.passfolio.domain.auth.oauth2.dto.OAuth2UserDto;
import com.capstone.passfolio.domain.user.entity.enums.Role;

@RequiredArgsConstructor
public class CustomOAuth2User implements OAuth2User {
    private final OAuth2UserDto oAuth2UserDto;

    @Override
    public Map<String, Object> getAttributes() { return Map.of(); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Collection<GrantedAuthority> collection = new ArrayList<>();
        collection.add((GrantedAuthority) () -> "ROLE_" + oAuth2UserDto.getRole().name());

        return collection;
    }

    public String getNickname() { return oAuth2UserDto.getNickname(); } // User field 가 nickname 이라 일관성을 위해 설정
    public String getUsername() { return oAuth2UserDto.getUsername(); }
    public Role getRole() { return oAuth2UserDto.getRole(); }
    public Long getUserId() { return oAuth2UserDto.getUserId(); }

    @Override public String getName() { return String.valueOf(oAuth2UserDto.getUserId()); } // PK 권장, 필수 오버라이드
}
