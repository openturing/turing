package com.viglet.turing.spring.security;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.context.SecurityContextHolder;

public class TurAuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of(((TurCustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUsername());
    }
}