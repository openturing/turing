package com.viglet.turing.spring.security;

import com.viglet.turing.properties.TurConfigProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Objects;
import java.util.Optional;

public class TurAuditorAwareImpl implements AuditorAware<String> {

	@Autowired
	private TurConfigProperties turConfigProperties;

	@Override
	public @NotNull Optional<String> getCurrentAuditor() {
		if (SecurityContextHolder.getContext().getAuthentication() != null)
			if (turConfigProperties.isKeycloak()) {
				OAuth2User user = ((OAuth2User) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
				return Optional
						.of(((String) Objects.requireNonNull(user.getAttribute("preferred_username"))).toLowerCase());
			}
		else {
				return Optional
						.of(((TurCustomUserDetails) SecurityContextHolder.getContext().getAuthentication()
								.getPrincipal()).getUsername().toLowerCase());
			}
		else
			return Optional.of("admin");
	}
}