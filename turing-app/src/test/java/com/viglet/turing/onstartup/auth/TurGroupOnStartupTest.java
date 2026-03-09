package com.viglet.turing.onstartup.auth;

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TurGroupOnStartupTest {

    @Mock
    private TurGroupRepository turGroupRepository;
    @Mock
    private TurRoleRepository turRoleRepository;

    @InjectMocks
    private TurGroupOnStartup turGroupOnStartup;

    @Test
    void shouldCreateDefaultAdminAndUserGroupsWhenNoGroupsExist() {
        TurRole adminRole = new TurRole("ROLE_ADMIN");
        TurRole userRole = new TurRole("ROLE_USER");

        when(turGroupRepository.findAll()).thenReturn(List.of());
        when(turRoleRepository.findByName("ROLE_ADMIN")).thenReturn(adminRole);
        when(turRoleRepository.findByName("ROLE_USER")).thenReturn(userRole);

        turGroupOnStartup.createDefaultRows();

        ArgumentCaptor<TurGroup> captor = ArgumentCaptor.forClass(TurGroup.class);
        verify(turGroupRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        List<TurGroup> groups = captor.getAllValues();
        assertEquals("Administrator", groups.get(0).getName());
        assertEquals("User", groups.get(1).getName());
    }

    @Test
    void shouldNotCreateGroupsWhenAlreadyExists() {
        when(turGroupRepository.findAll()).thenReturn(List.of(new TurGroup()));

        turGroupOnStartup.createDefaultRows();

        verify(turGroupRepository, never()).save(org.mockito.ArgumentMatchers.any(TurGroup.class));
    }
}
