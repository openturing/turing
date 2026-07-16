package com.viglet.turing.spring.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.viglet.turing.properties.TurConfigProperties;

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
    private final TurConfigProperties turConfigProperties;

    public TurOidcUserService(TurAuthorityResolver turAuthorityResolver,
            TurConfigProperties turConfigProperties) {
        this.turAuthorityResolver = turAuthorityResolver;
        this.turConfigProperties = turConfigProperties;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        if (oidcUser == null) {
            throw new OAuth2AuthenticationException("invalid_user_info_response");
        }

        // Client Silos isolation (C051): on a dedicated per-client instance, reject
        // a login whose viglet_client claim doesn't match this instance's client.
        TurClientClaimGate.enforce(turConfigProperties.getRequiredVigletClient(),
                oidcUser.getClaims().get(TurClientClaimGate.VIGLET_CLIENT_CLAIM));

        String username = oidcUser.getAttribute(PREFERRED_USERNAME);
        if (username == null) {
            username = oidcUser.getAttribute(EMAIL);
        }

        Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());
        // T366 — map the Keycloak `platform-admin` realm role → ROLE_PLATFORM_ADMIN
        Set<String> realmRoles = TurAuthorityResolver.extractRealmRoles(oidcUser.getClaims());
        turAuthorityResolver.resolve(username, realmRoles, authorities);

        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }
}
