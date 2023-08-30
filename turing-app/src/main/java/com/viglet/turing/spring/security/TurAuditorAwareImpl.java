package com.viglet.turing.spring.security;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.context.SecurityContextHolder;

public class TurAuditorAwareImpl implements AuditorAware<String> {

	@Override
	public Optional<String> getCurrentAuditor() {
		if (SecurityContextHolder.getContext().getAuthentication() != null)
			return Optional
					.of(((TurCustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
							.getUsername().toLowerCase());
		else
			return Optional.of("admin");
	}
}