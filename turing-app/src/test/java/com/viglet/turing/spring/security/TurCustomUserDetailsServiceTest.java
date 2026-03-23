package com.viglet.turing.spring.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurPrivilegeRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.properties.TurConfigProperties;

@ExtendWith(MockitoExtension.class)
class TurCustomUserDetailsServiceTest {

    @Mock
    private TurUserRepository turUserRepository;
    @Mock
    private TurRoleRepository turRoleRepository;
    @Mock
    private TurGroupRepository turGroupRepository;
    @Mock
    private TurPrivilegeRepository turPrivilegeRepository;
    @Mock
    private TurConfigProperties turConfigProperties;
    @InjectMocks
    private TurCustomUserDetailsService service;

    @Test
    void loadUserByUsernameShouldReturnUserDetailsWithRoles() {
        when(turConfigProperties.isPermissions()).thenReturn(true);

        TurUser user = TurUser.builder().username("admin").password("pass").build();
        when(turUserRepository.findByUsername("admin")).thenReturn(user);

        TurGroup group = mock(TurGroup.class);
        when(turGroupRepository.findByTurUsersContaining(user)).thenReturn(Set.of(group));

        TurRole role = mock(TurRole.class);
        when(role.getName()).thenReturn("ROLE_ADMIN");
        when(turRoleRepository.findByTurGroupsContaining(group)).thenReturn(Set.of(role));

        UserDetails details = service.loadUserByUsername("admin");

        assertNotNull(details);
        assertEquals("admin", details.getUsername());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void loadUserByUsernameShouldThrowWhenUserNotFound() {
        when(turUserRepository.findByUsername("unknown")).thenReturn(null);

        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("unknown"));
    }

    @Test
    void loadUserByUsernameShouldReturnDetailsWithEmptyRolesIfNoGroups() {
        when(turConfigProperties.isPermissions()).thenReturn(true);

        TurUser user = TurUser.builder().username("nogroups").password("pass").build();
        when(turUserRepository.findByUsername("nogroups")).thenReturn(user);
        when(turGroupRepository.findByTurUsersContaining(user)).thenReturn(Collections.emptySet());

        UserDetails details = service.loadUserByUsername("nogroups");

        assertNotNull(details);
        assertTrue(details.getAuthorities().isEmpty());
    }
}
