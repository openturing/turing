package com.viglet.turing.spring.security;

import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import com.viglet.turing.properties.TurConfigProperties;

@Component
public class TurAuditorAwareImpl implements AuditorAware<String> {

    public static final String ADMIN = "admin";
    public static final String PREFERRED_USERNAME = "preferred_username";

    private final TurConfigProperties turConfigProperties;

    public TurAuditorAwareImpl(TurConfigProperties turConfigProperties) {
        this.turConfigProperties = turConfigProperties;
    }

    @Override
    public @NotNull Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.of(ADMIN);
        }
        Object principal = authentication.getPrincipal();
        if (principal == null) {
            return Optional.of(ADMIN);
        }
        if (turConfigProperties.isKeycloak()) {
            OAuth2User user = (OAuth2User) principal;
            return Optional
                    .of(((String) Objects.requireNonNull(user.getAttribute(PREFERRED_USERNAME))).toLowerCase());
        }
        return Optional.of(((TurCustomUserDetails) principal).getUsername().toLowerCase());
    }
}
