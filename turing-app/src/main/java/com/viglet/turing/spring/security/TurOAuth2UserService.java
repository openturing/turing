package com.viglet.turing.spring.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.viglet.turing.properties.TurConfigProperties;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Enriches OAuth2 (non-OIDC) users with authorities from the local database.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.17
 */
@Service
public class TurOAuth2UserService extends DefaultOAuth2UserService {

    private static final String PREFERRED_USERNAME = "preferred_username";
    private static final String LOGIN = "login";
    private static final String EMAIL = "email";

    private final TurAuthorityResolver turAuthorityResolver;
    private final TurConfigProperties turConfigProperties;

    public TurOAuth2UserService(TurAuthorityResolver turAuthorityResolver,
            TurConfigProperties turConfigProperties) {
        this.turAuthorityResolver = turAuthorityResolver;
        this.turConfigProperties = turConfigProperties;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        if (oAuth2User == null) {
            throw new OAuth2AuthenticationException("invalid_user_info_response");
        }

        // Client Silos isolation (C051): reject a login whose viglet_client claim
        // doesn't match this instance's dedicated client (no-op if unrestricted).
        TurClientClaimGate.enforce(turConfigProperties.getRequiredVigletClient(),
                oAuth2User.getAttribute(TurClientClaimGate.VIGLET_CLIENT_CLAIM));

        String username = oAuth2User.getAttribute(PREFERRED_USERNAME);
        if (username == null) {
            username = oAuth2User.getAttribute(LOGIN);
        }
        if (username == null) {
            username = oAuth2User.getAttribute(EMAIL);
        }

        Set<GrantedAuthority> authorities = new HashSet<>(oAuth2User.getAuthorities());
        // T366 — map the Keycloak `platform-admin` realm role → ROLE_PLATFORM_ADMIN
        Set<String> realmRoles = TurAuthorityResolver.extractRealmRoles(oAuth2User.getAttributes());
        turAuthorityResolver.resolve(username, realmRoles, authorities);

        String nameAttributeKey = Objects.requireNonNull(
                userRequest.getClientRegistration().getProviderDetails()
                        .getUserInfoEndpoint().getUserNameAttributeName(),
                "OAuth2 user-name attribute key must be configured");
        return new DefaultOAuth2User(authorities, oAuth2User.getAttributes(), nameAttributeKey);
    }
}
