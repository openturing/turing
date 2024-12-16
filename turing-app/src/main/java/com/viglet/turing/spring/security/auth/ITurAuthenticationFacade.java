package com.viglet.turing.spring.security.auth;

import org.springframework.security.core.Authentication;

public interface ITurAuthenticationFacade {
    Authentication getAuthentication();
}