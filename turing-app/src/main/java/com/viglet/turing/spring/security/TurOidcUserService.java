package com.viglet.turing.spring.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Enriches OIDC users (Keycloak with scope=openid) with authorities
 * from the local database. This is the service actually invoked by
 * Spring Security when the provider uses OpenID Connect.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Service
public class TurOidcUserService extends OidcUserService {

    private static final String PREFERRED_USERNAME = "preferred_username";
    private static final String EMAIL = "email";

    private final TurAuthorityResolver turAuthorityResolver;

    public TurOidcUserService(TurAuthorityResolver turAuthorityResolver) {
        this.turAuthorityResolver = turAuthorityResolver;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String username = oidcUser.getAttribute(PREFERRED_USERNAME);
        if (username == null) {
            username = oidcUser.getAttribute(EMAIL);
        }

        Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());
        turAuthorityResolver.resolve(username, authorities);

        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }
}
