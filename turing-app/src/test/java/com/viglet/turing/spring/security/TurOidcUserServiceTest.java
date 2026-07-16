package com.viglet.turing.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import com.viglet.turing.properties.TurConfigProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Tests for TurOidcUserService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurOidcUserServiceTest {
    // Fixed token timestamps — the user service reads claims only; it never
    // validates expiry against the clock, so a literal keeps these deterministic.
    private static final Instant FIXED_NOW = Instant.parse("2026-06-15T12:00:00Z");


    @Mock
    private TurAuthorityResolver turAuthorityResolver;

    @Test
    void shouldDelegateToAuthorityResolverWithPreferredUsername() {
        TurOidcUserService testService = createTestService("oidcuser");

        OidcUserRequest userRequest = createOidcUserRequest("oidcuser");
        testService.loadUser(userRequest);

        verify(turAuthorityResolver).resolve(eq("oidcuser"), any());
    }

    @Test
    void shouldEnrichAuthoritiesFromResolver() {
        TurOidcUserService testService = createTestService("enriched");

        doAnswer(invocation -> {
            Set<GrantedAuthority> authorities = invocation.getArgument(1);
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            return null;
        }).when(turAuthorityResolver).resolve(eq("enriched"), any());

        OidcUserRequest userRequest = createOidcUserRequest("enriched");
        OidcUser result = testService.loadUser(userRequest);

        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN");
    }

    @Test
    void shouldPreserveOriginalAuthorities() {
        OidcIdToken idToken = createIdToken("preserved");
        OidcUserInfo userInfo = createUserInfo("preserved");
        Set<GrantedAuthority> originalAuth = Set.of(
                new SimpleGrantedAuthority("SCOPE_openid"));

        TurOidcUserService testService = createTestServiceWithBaseUser(
                new DefaultOidcUser(originalAuth, idToken, userInfo));

        OidcUserRequest userRequest = createOidcUserRequest("preserved");
        OidcUser result = testService.loadUser(userRequest);

        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("SCOPE_openid");
    }

    @Test
    void shouldHandleNullPreferredUsername() {
        OidcIdToken idToken = OidcIdToken.withTokenValue("token")
                .claim("sub", "no-pref")
                .issuedAt(FIXED_NOW)
                .expiresAt(FIXED_NOW.plusSeconds(3600))
                .build();

        TurOidcUserService testService = createTestServiceWithBaseUser(
                new DefaultOidcUser(
                        Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                        idToken));

        OidcUserRequest userRequest = createOidcUserRequest("ignored");
        OidcUser result = testService.loadUser(userRequest);

        verify(turAuthorityResolver).resolve(eq(null), any());
        assertThat(result).isNotNull();
    }

    @Test
    void shouldCombineOriginalAndResolvedAuthorities() {
        OidcIdToken idToken = createIdToken("combo");
        OidcUserInfo userInfo = createUserInfo("combo");
        DefaultOidcUser baseUser = new DefaultOidcUser(
                Set.of(new SimpleGrantedAuthority("SCOPE_openid"),
                        new SimpleGrantedAuthority("SCOPE_profile")),
                idToken, userInfo);

        TurOidcUserService testService = createTestServiceWithBaseUser(baseUser);

        doAnswer(invocation -> {
            Set<GrantedAuthority> authorities = invocation.getArgument(1);
            authorities.add(new SimpleGrantedAuthority("ROLE_EDITOR"));
            authorities.add(new SimpleGrantedAuthority("PRIV_MANAGE"));
            return null;
        }).when(turAuthorityResolver).resolve(eq("combo"), any());

        OidcUserRequest userRequest = createOidcUserRequest("combo");
        OidcUser result = testService.loadUser(userRequest);

        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("SCOPE_openid", "SCOPE_profile", "ROLE_EDITOR", "PRIV_MANAGE");
    }

    @Test
    void shouldReturnOidcUserWithIdTokenAndUserInfo() {
        OidcIdToken idToken = createIdToken("tokenuser");
        OidcUserInfo userInfo = createUserInfo("tokenuser");
        DefaultOidcUser baseUser = new DefaultOidcUser(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                idToken, userInfo);

        TurOidcUserService testService = createTestServiceWithBaseUser(baseUser);

        OidcUserRequest userRequest = createOidcUserRequest("tokenuser");
        OidcUser result = testService.loadUser(userRequest);

        assertThat(result.getIdToken()).isNotNull();
        assertThat(result.getIdToken().getSubject()).isEqualTo("sub-tokenuser");
        assertThat(result.getUserInfo()).isNotNull();
        assertThat(result.getUserInfo().getPreferredUsername()).isEqualTo("tokenuser");
    }

    @Test
    void shouldReturnDefaultOidcUserType() {
        TurOidcUserService testService = createTestService("typecheck");

        OidcUserRequest userRequest = createOidcUserRequest("typecheck");
        OidcUser result = testService.loadUser(userRequest);

        assertThat(result).isInstanceOf(DefaultOidcUser.class);
    }

    private TurOidcUserService createTestService(String username) {
        OidcIdToken idToken = createIdToken(username);
        OidcUserInfo userInfo = createUserInfo(username);
        DefaultOidcUser baseUser = new DefaultOidcUser(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                idToken, userInfo);
        return createTestServiceWithBaseUser(baseUser);
    }

    private TurOidcUserService createTestServiceWithBaseUser(OidcUser baseUser) {
        return new TurOidcUserService(turAuthorityResolver, new TurConfigProperties()) {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) {
                Set<GrantedAuthority> authorities = new HashSet<>(baseUser.getAuthorities());
                turAuthorityResolver.resolve(
                        baseUser.getAttribute("preferred_username"), authorities);
                return new DefaultOidcUser(authorities, baseUser.getIdToken(), baseUser.getUserInfo());
            }
        };
    }

    private OidcIdToken createIdToken(String username) {
        return OidcIdToken.withTokenValue("id-token-" + username)
                .claim("sub", "sub-" + username)
                .claim("preferred_username", username)
                .issuedAt(FIXED_NOW)
                .expiresAt(FIXED_NOW.plusSeconds(3600))
                .build();
    }

    private OidcUserInfo createUserInfo(String username) {
        return OidcUserInfo.builder()
                .subject("sub-" + username)
                .preferredUsername(username)
                .email(username + "@example.com")
                .build();
    }

    private OidcUserRequest createOidcUserRequest(String username) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("test-client")
                .redirectUri("http://localhost/callback")
                .authorizationUri("http://localhost/auth")
                .tokenUri("http://localhost/token")
                .userInfoUri("http://localhost/userinfo")
                .userNameAttributeName("sub")
                .scope("openid", "profile")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-access-token",
                FIXED_NOW,
                FIXED_NOW.plusSeconds(3600));

        OidcIdToken idToken = createIdToken(username);

        return new OidcUserRequest(registration, accessToken, idToken);
    }
}
