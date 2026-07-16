package com.viglet.turing.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.viglet.turing.properties.TurConfigProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Tests for TurOAuth2UserService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurOAuth2UserServiceTest {
    // Fixed token timestamps — the user service reads claims only; it never
    // validates expiry against the clock, so a literal keeps these deterministic.
    private static final Instant FIXED_NOW = Instant.parse("2026-06-15T12:00:00Z");


    @Mock
    private TurAuthorityResolver turAuthorityResolver;

    @Test
    void shouldDelegateToAuthorityResolverWithPreferredUsername() {
        TurOAuth2UserService testService = createTestService("oauth2user");

        OAuth2UserRequest userRequest = createUserRequest();
        testService.loadUser(userRequest);

        verify(turAuthorityResolver).resolve(eq("oauth2user"), any());
    }

    @Test
    void shouldEnrichAuthoritiesFromResolver() {
        TurOAuth2UserService testService = createTestService("enriched");

        doAnswer(invocation -> {
            Set<GrantedAuthority> authorities = invocation.getArgument(1);
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            return null;
        }).when(turAuthorityResolver).resolve(eq("enriched"), any());

        OAuth2UserRequest userRequest = createUserRequest();
        OAuth2User result = testService.loadUser(userRequest);

        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN");
    }

    @Test
    void shouldPreserveOriginalAuthorities() {
        OAuth2User baseUser = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("SCOPE_read")),
                Map.of("preferred_username", "preserved", "sub", "123"),
                "sub");

        TurOAuth2UserService testService = createTestServiceWithBaseUser(baseUser);

        OAuth2UserRequest userRequest = createUserRequest();
        OAuth2User result = testService.loadUser(userRequest);

        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("SCOPE_read");
    }

    @Test
    void shouldHandleNullPreferredUsername() {
        OAuth2User baseUser = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "456"),
                "sub");

        TurOAuth2UserService testService = createTestServiceWithBaseUser(baseUser);

        OAuth2UserRequest userRequest = createUserRequest();
        OAuth2User result = testService.loadUser(userRequest);

        verify(turAuthorityResolver).resolve(eq(null), any());
        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnUserWithCorrectAttributes() {
        OAuth2User baseUser = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("preferred_username", "attruser", "sub", "789", "email", "test@example.com"),
                "sub");

        TurOAuth2UserService testService = createTestServiceWithBaseUser(baseUser);

        OAuth2UserRequest userRequest = createUserRequest();
        OAuth2User result = testService.loadUser(userRequest);

        assertThat(result.getAttributes())
                .containsEntry("preferred_username", "attruser")
                .containsEntry("email", "test@example.com");
    }

    @Test
    void shouldCombineOriginalAndResolvedAuthorities() {
        OAuth2User baseUser = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("SCOPE_openid")),
                Map.of("preferred_username", "combo", "sub", "c1"),
                "sub");

        TurOAuth2UserService testService = createTestServiceWithBaseUser(baseUser);

        doAnswer(invocation -> {
            Set<GrantedAuthority> authorities = invocation.getArgument(1);
            authorities.add(new SimpleGrantedAuthority("ROLE_EDITOR"));
            authorities.add(new SimpleGrantedAuthority("PRIV_SEARCH"));
            return null;
        }).when(turAuthorityResolver).resolve(eq("combo"), any());

        OAuth2UserRequest userRequest = createUserRequest();
        OAuth2User result = testService.loadUser(userRequest);

        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("SCOPE_openid", "ROLE_EDITOR", "PRIV_SEARCH");
    }

    @Test
    void shouldUseUserNameAttributeFromClientRegistration() {
        OAuth2User baseUser = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("preferred_username", "nameattr", "sub", "n1"),
                "sub");

        TurOAuth2UserService testService = createTestServiceWithBaseUser(baseUser);

        OAuth2UserRequest userRequest = createUserRequest();
        OAuth2User result = testService.loadUser(userRequest);

        assertThat(result.getName()).isEqualTo("n1");
    }

    private TurOAuth2UserService createTestService(String username) {
        OAuth2User baseUser = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("preferred_username", username, "sub", "sub-" + username),
                "sub");
        return createTestServiceWithBaseUser(baseUser);
    }

    private TurOAuth2UserService createTestServiceWithBaseUser(OAuth2User baseUser) {
        return new TurOAuth2UserService(turAuthorityResolver, new TurConfigProperties()) {
            @Override
            public OAuth2User loadUser(OAuth2UserRequest userRequest) {
                Set<GrantedAuthority> authorities = new HashSet<>(baseUser.getAuthorities());
                turAuthorityResolver.resolve(
                        baseUser.getAttribute("preferred_username"), authorities);

                return new DefaultOAuth2User(authorities, baseUser.getAttributes(),
                        userRequest.getClientRegistration().getProviderDetails()
                                .getUserInfoEndpoint().getUserNameAttributeName());
            }
        };
    }

    private OAuth2UserRequest createUserRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("test-client")
                .redirectUri("http://localhost/callback")
                .authorizationUri("http://localhost/auth")
                .tokenUri("http://localhost/token")
                .userInfoUri("http://localhost/userinfo")
                .userNameAttributeName("sub")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-token",
                FIXED_NOW,
                FIXED_NOW.plusSeconds(3600));

        return new OAuth2UserRequest(registration, accessToken);
    }
}
