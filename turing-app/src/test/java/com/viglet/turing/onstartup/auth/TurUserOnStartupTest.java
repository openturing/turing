package com.viglet.turing.onstartup.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;

@ExtendWith(MockitoExtension.class)
class TurUserOnStartupTest {

    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TurUserRepository turUserRepository;
    @Mock
    private TurGroupRepository turGroupRepository;

    @InjectMocks
    private TurUserOnStartup turUserOnStartup;

    @Test
    void shouldCreateDefaultAdminUserWhenRepositoryIsEmpty() {
        TurGroup adminGroup = new TurGroup();
        adminGroup.setName("Administrator");

        when(turUserRepository.findAll()).thenReturn(List.of());
        when(passwordEncoder.encode("secret12")).thenReturn("encoded-secret12");
        when(turGroupRepository.findByName("Administrator")).thenReturn(adminGroup);

        turUserOnStartup.createDefaultRows("secret12");

        ArgumentCaptor<TurUser> captor = ArgumentCaptor.forClass(TurUser.class);
        verify(turUserRepository).save(captor.capture());
        TurUser savedUser = captor.getValue();

        assertEquals("admin", savedUser.getUsername());
        assertEquals("encoded-secret12", savedUser.getPassword());
        assertEquals(1, savedUser.getEnabled());
        assertNotNull(savedUser.getLastLogin());
    }

    @Test
    void shouldNotCreateDefaultUserWhenUsersAlreadyExist() {
        when(turUserRepository.findAll()).thenReturn(List.of(TurUser.builder().username("existing").build()));

        turUserOnStartup.createDefaultRows("secret12");

        verify(turUserRepository, never()).save(org.mockito.ArgumentMatchers.any(TurUser.class));
    }
}
