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

import com.viglet.turing.persistence.model.auth.TurPrivilege;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.repository.auth.TurPrivilegeRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;

@ExtendWith(MockitoExtension.class)
class TurRoleOnStartupTest {

    @Mock
    private TurPrivilegeRepository turPrivilegeRepository;
    @Mock
    private TurRoleRepository turRoleRepository;

    @InjectMocks
    private TurRoleOnStartup turRoleOnStartup;

    @Test
    void shouldCreatePrivilegeWhenNotFound() {
        when(turPrivilegeRepository.findByName("READ_PRIVILEGE")).thenReturn(null);

        TurPrivilege privilege = turRoleOnStartup.createPrivilegeIfNotFound("READ_PRIVILEGE");

        assertNotNull(privilege);
        assertEquals("READ_PRIVILEGE", privilege.getName());
        verify(turPrivilegeRepository).save(org.mockito.ArgumentMatchers.any(TurPrivilege.class));
    }

    @Test
    void shouldNotCreatePrivilegeWhenAlreadyExists() {
        TurPrivilege existing = new TurPrivilege("WRITE_PRIVILEGE");
        when(turPrivilegeRepository.findByName("WRITE_PRIVILEGE")).thenReturn(existing);

        TurPrivilege privilege = turRoleOnStartup.createPrivilegeIfNotFound("WRITE_PRIVILEGE");

        assertEquals(existing, privilege);
        verify(turPrivilegeRepository, never()).save(org.mockito.ArgumentMatchers.any(TurPrivilege.class));
    }

    @Test
    void shouldCreateRoleWhenNotFound() {
        TurPrivilege read = new TurPrivilege("READ_PRIVILEGE");
        TurPrivilege write = new TurPrivilege("WRITE_PRIVILEGE");
        when(turRoleRepository.findByName("ROLE_ADMIN")).thenReturn(null);

        turRoleOnStartup.createRoleIfNotFound("ROLE_ADMIN", List.of(read, write));

        ArgumentCaptor<TurRole> captor = ArgumentCaptor.forClass(TurRole.class);
        verify(turRoleRepository).save(captor.capture());
        assertEquals("ROLE_ADMIN", captor.getValue().getName());
        assertEquals(2, captor.getValue().getTurPrivileges().size());
    }

    @Test
    void shouldCreateDefaultRowsForAdminAndUserRoles() {
        when(turPrivilegeRepository.findByName("READ_PRIVILEGE")).thenReturn(null);
        when(turPrivilegeRepository.findByName("WRITE_PRIVILEGE")).thenReturn(null);
        when(turRoleRepository.findByName("ROLE_ADMIN")).thenReturn(null);
        when(turRoleRepository.findByName("ROLE_USER")).thenReturn(null);

        turRoleOnStartup.createDefaultRows();

        verify(turPrivilegeRepository, org.mockito.Mockito.times(2))
                .save(org.mockito.ArgumentMatchers.any(TurPrivilege.class));
        verify(turRoleRepository, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.any(TurRole.class));
    }
}
