package com.viglet.turing.spring.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.properties.TurConfigProperties;

@ExtendWith(MockitoExtension.class)
class TurAuditorAwareImplTest {

    @Mock
    private TurConfigProperties turConfigProperties;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnAdminWhenAuthenticationIsNull() {
        TurAuditorAwareImpl auditorAware = new TurAuditorAwareImpl(turConfigProperties);

        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertEquals(Optional.of("admin"), auditor);
    }

    @Test
    void shouldReturnLowercasePreferredUsernameWhenKeycloakIsEnabled() {
        when(turConfigProperties.isKeycloak()).thenReturn(true);
        OAuth2User oauth2User = org.mockito.Mockito.mock(OAuth2User.class);
        when(oauth2User.getAttribute(TurAuditorAwareImpl.PREFERRED_USERNAME)).thenReturn("John.DOE");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(oauth2User, null));

        TurAuditorAwareImpl auditorAware = new TurAuditorAwareImpl(turConfigProperties);
        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertEquals(Optional.of("john.doe"), auditor);
    }

    @Test
    void shouldReturnLowercaseUsernameWhenKeycloakIsDisabled() {
        when(turConfigProperties.isKeycloak()).thenReturn(false);

        TurUser turUser = TurUser.builder().username("ADMIN.User").build();
        TurCustomUserDetails principal = new TurCustomUserDetails(turUser, java.util.List.of("ROLE_USER"));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null));

        TurAuditorAwareImpl auditorAware = new TurAuditorAwareImpl(turConfigProperties);
        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertEquals(Optional.of("admin.user"), auditor);
    }
}
