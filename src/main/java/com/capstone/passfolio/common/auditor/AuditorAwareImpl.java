package com.capstone.passfolio.common.auditor;

import com.capstone.passfolio.system.security.model.UserPrincipal;
import lombok.NonNull;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    @NonNull
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            return Optional.empty();
        }

        Long userId = userPrincipal.getUserId();
        if (userId == null) {
            return Optional.empty();
        }

        return Optional.of(userPrincipal.getUserId());
    }
}