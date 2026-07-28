package com.viglet.turing.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.properties.TurConfigProperties;

@ExtendWith(MockitoExtension.class)
class TurAdminPasswordServiceTest {

    @Mock
    private TurUserRepository turUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private TurConfigProperties turConfigProperties;
    private TurAdminPasswordService turAdminPasswordService;

    @BeforeEach
    void setUp() {
        turConfigProperties = new TurConfigProperties();
        turAdminPasswordService = new TurAdminPasswordService(turUserRepository, passwordEncoder,
                turConfigProperties);
    }

    private void givenEncoderReturnsHash() {
        when(passwordEncoder.encode(any(CharSequence.class))).thenReturn("$2a$10$hash");
    }

    @Test
    void shouldRequireSetupWhenAdminHasNoPassword() {
        when(turUserRepository.findByUsername("admin")).thenReturn(TurUser.builder().username("admin").build());

        assertTrue(turAdminPasswordService.isSetupRequired());
    }

    @Test
    void shouldNotRequireSetupWhenAdminAlreadyHasPassword() {
        when(turUserRepository.findByUsername("admin"))
                .thenReturn(TurUser.builder().username("admin").password("$2a$10$existing").build());

        assertFalse(turAdminPasswordService.isSetupRequired());
    }

    @Test
    void shouldCreateAdminWhenSettingPasswordOnMissingAccount() {
        givenEncoderReturnsHash();
        when(turUserRepository.findByUsername("admin")).thenReturn(null);

        turAdminPasswordService.setPassword("secret123");

        TurUser saved = captureSavedUser();
        assertEquals("admin", saved.getUsername());
        assertEquals("$2a$10$hash", saved.getPassword());
        assertEquals(1, saved.getEnabled());
    }

    @Test
    void shouldRejectPasswordShorterThanMinimumSize() {
        assertThrows(IllegalArgumentException.class, () -> turAdminPasswordService.setPassword("admin"));
        verify(turUserRepository, never()).save(any(TurUser.class));
    }

    @Test
    void shouldRejectBlankPassword() {
        assertThrows(IllegalArgumentException.class, () -> turAdminPasswordService.setPassword("   "));
        verify(turUserRepository, never()).save(any(TurUser.class));
    }

    @Test
    void shouldBootstrapAdminPasswordFromConfiguration() {
        givenEncoderReturnsHash();
        turConfigProperties.getAdmin().setPassword("docker123");
        when(turUserRepository.findByUsername("admin")).thenReturn(TurUser.builder().username("admin").build());

        turAdminPasswordService.bootstrapFromConfiguration();

        assertEquals("$2a$10$hash", captureSavedUser().getPassword());
    }

    @Test
    void shouldDoNothingWhenConfiguredPasswordIsBlank() {
        turConfigProperties.getAdmin().setPassword("");

        turAdminPasswordService.bootstrapFromConfiguration();

        verify(turUserRepository, never()).save(any(TurUser.class));
    }

    @Test
    void shouldNotOverwriteAnAlreadyConfiguredPassword() {
        turConfigProperties.getAdmin().setPassword("docker123");
        when(turUserRepository.findByUsername("admin"))
                .thenReturn(TurUser.builder().username("admin").password("$2a$10$existing").build());

        turAdminPasswordService.bootstrapFromConfiguration();

        verify(turUserRepository, never()).save(any(TurUser.class));
    }

    @Test
    void shouldIgnoreTooShortConfiguredPasswordWithoutFailingStartup() {
        turConfigProperties.getAdmin().setPassword("admin");
        when(turUserRepository.findByUsername("admin")).thenReturn(TurUser.builder().username("admin").build());

        turAdminPasswordService.bootstrapFromConfiguration();

        verify(turUserRepository, never()).save(any(TurUser.class));
    }

    private TurUser captureSavedUser() {
        ArgumentCaptor<TurUser> captor = ArgumentCaptor.forClass(TurUser.class);
        verify(turUserRepository).save(captor.capture());
        return captor.getValue();
    }
}
