package com.capstone.passfolio.system.security.model;

import com.capstone.passfolio.domain.auth.oauth2.entity.CustomOAuth2User;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.entity.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserPrincipal implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId; // OAuth2 User -> Nullable
    private String username;
    private String password; // OAuth2 User -> Nullable
    private Role role;

    public static UserPrincipal from(User u) { // Common User
        return UserPrincipal.builder()
                .userId(u.getId())
                .username(u.getUsername())
                .role(u.getRole())
                .build();
    }

    public static UserPrincipal toOAuth2(CustomOAuth2User customOAuth2User) {
        return UserPrincipal.builder()
                .userId(customOAuth2User.getUserId())
                .username(customOAuth2User.getUsername())
                .role(Objects.requireNonNull(customOAuth2User.getRole()))
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_"+role.name()));
    }

    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return password; } // core options -> cannot remove it

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserPrincipal that)) return false;
        return Objects.equals(this.userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.userId);
    }
}
