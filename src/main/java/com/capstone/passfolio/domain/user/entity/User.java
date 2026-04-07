package com.capstone.passfolio.domain.user.entity;

import com.capstone.passfolio.common.auditor.TimeBaseEntity;
import com.capstone.passfolio.domain.auth.oauth2.entity.enums.ProviderType;
import com.capstone.passfolio.domain.user.entity.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "USER_USERNAME", columnNames = "username")}
)
public class User extends TimeBaseEntity {
    // TODO: Change to Snowflake
    @Id @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String username;

    @Column
    private String profileImageUrl;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.USER;

    @PrePersist // INSERT 되기 전 실행 (새로운 User 저장 시)
    @PreUpdate  // UPDATE 되기 전 실행 (기존 User 수정 시)
    private void normalize() {
        if (this.nickname != null) this.nickname = this.nickname.trim(); // "hades " 같은 공백 포함 문자열 방지
    }

    // JPA Dirty Checking
    // public void softDelete(AuthDto.SoftDeleteDto dto) {
    //     if (dto == null) return;
    //
    //     this.username = dto.getUsername();
    //     this.nickname = dto.getNickname();
    //     this.profileImageUrl = dto.getProfileImageUrl();
    // }

    // OAuth2 사용자 정보 업데이트 (login 시점에서 기존 DB 데이터와 매칭해서 다르면 업데이트. 사용자가 직접 수정 X)
    public void updateOAuth2Info(String nickname, String email) {
        this.nickname = nonBlankOrDefault(nickname, this.nickname);
        this.profileImageUrl = nonBlankOrDefault(email, this.profileImageUrl);
    }

    public void updateProfileImageUrl(String profileImageUrl) {
        if (profileImageUrl != null && !profileImageUrl.isBlank()) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    private <T> T nonBlankOrDefault(T newValue, T currentValue) {
        return newValue != null ? newValue : currentValue;
    }
}
