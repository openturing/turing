package com.viglet.turing.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurPrivilege;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurPrivilegeRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.properties.TurConfigProperties;

/**
 * Tests for TurCustomUserDetailsService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
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
    private TurAuthorityResolver turAuthorityResolver;
    @Mock
    private TurConfigProperties turConfigProperties;
    @InjectMocks
    private TurCustomUserDetailsService service;

    @org.junit.jupiter.api.BeforeEach
    void stubTenantAuthorities() {
        org.mockito.Mockito.lenient()
                .when(turAuthorityResolver.tenantAuthorities(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Set.of());
    }

    @Test
    void loadUserByUsernameShouldReturnUserDetailsWithRoles() {
        when(turConfigProperties.isPermissions()).thenReturn(true);

        TurUser user = TurUser.builder().username("admin").password("pass").build();
        when(turUserRepository.findByUsername("admin")).thenReturn(user);

        TurGroup group = mock(TurGroup.class);
        when(turGroupRepository.findByTurUsersContaining(user)).thenReturn(Set.of(group));

        TurRole role = mock(TurRole.class);
        when(role.getName()).thenReturn("ROLE_ADMIN");
        when(role.getTurPrivileges()).thenReturn(Collections.emptyList());
        when(turRoleRepository.findByTurGroupsContaining(group)).thenReturn(Set.of(role));

        UserDetails details = service.loadUserByUsername("admin");

        assertThat(details).isNotNull();
        assertThat(details.getUsername()).isEqualTo("admin");
        assertThat(details.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Test
    void loadUserByUsernameShouldThrowWhenUserNotFound() {
        when(turUserRepository.findByUsername("unknown")).thenReturn(null);

        assertThatThrownBy(() -> service.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("No user present with username: unknown");
    }

    @Test
    void loadUserByUsernameShouldReturnDetailsWithEmptyRolesIfNoGroups() {
        when(turConfigProperties.isPermissions()).thenReturn(true);

        TurUser user = TurUser.builder().username("nogroups").password("pass").build();
        when(turUserRepository.findByUsername("nogroups")).thenReturn(user);
        when(turGroupRepository.findByTurUsersContaining(user)).thenReturn(Collections.emptySet());

        UserDetails details = service.loadUserByUsername("nogroups");

        assertThat(details).isNotNull();
        assertThat(details.getAuthorities()).isEmpty();
    }

    @Test
    void shouldGrantAllAuthoritiesWhenPermissionsDisabled() {
        when(turConfigProperties.isPermissions()).thenReturn(false);

        TurUser user = TurUser.builder().username("anyuser").password("pass").build();
        when(turUserRepository.findByUsername("anyuser")).thenReturn(user);

        TurPrivilege privilege1 = new TurPrivilege("PRIV_READ");
        TurPrivilege privilege2 = new TurPrivilege("PRIV_WRITE");
        when(turPrivilegeRepository.findAll()).thenReturn(List.of(privilege1, privilege2));

        UserDetails details = service.loadUserByUsername("anyuser");

        assertThat(details).isNotNull();
        assertThat(details.getAuthorities())
                .extracting(a -> a.getAuthority())
                .contains("ROLE_ADMIN", "PRIV_READ", "PRIV_WRITE");
    }

    @Test
    void shouldNotQueryGroupsWhenPermissionsDisabled() {
        when(turConfigProperties.isPermissions()).thenReturn(false);

        TurUser user = TurUser.builder().username("testuser").password("pass").build();
        when(turUserRepository.findByUsername("testuser")).thenReturn(user);
        when(turPrivilegeRepository.findAll()).thenReturn(Collections.emptyList());

        service.loadUserByUsername("testuser");

        verifyNoInteractions(turGroupRepository);
        verifyNoInteractions(turRoleRepository);
    }

    @Test
    void shouldIncludePrivilegesFromRoles() {
        when(turConfigProperties.isPermissions()).thenReturn(true);

        TurUser user = TurUser.builder().username("privuser").password("pass").build();
        when(turUserRepository.findByUsername("privuser")).thenReturn(user);

        TurGroup group = mock(TurGroup.class);
        when(turGroupRepository.findByTurUsersContaining(user)).thenReturn(Set.of(group));

        TurPrivilege privilege = new TurPrivilege("PRIV_MANAGE_SITES");
        TurRole role = mock(TurRole.class);
        when(role.getName()).thenReturn("ROLE_EDITOR");
        when(role.getTurPrivileges()).thenReturn(List.of(privilege));
        when(turRoleRepository.findByTurGroupsContaining(group)).thenReturn(Set.of(role));

        UserDetails details = service.loadUserByUsername("privuser");

        assertThat(details.getAuthorities())
                .extracting(a -> a.getAuthority())
                .contains("ROLE_EDITOR", "PRIV_MANAGE_SITES");
    }

    @Test
    void shouldHandleMultipleGroupsWithMultipleRoles() {
        when(turConfigProperties.isPermissions()).thenReturn(true);

        TurUser user = TurUser.builder().username("multiuser").password("pass").build();
        when(turUserRepository.findByUsername("multiuser")).thenReturn(user);

        TurGroup group1 = mock(TurGroup.class);
        TurGroup group2 = mock(TurGroup.class);
        when(turGroupRepository.findByTurUsersContaining(user)).thenReturn(Set.of(group1, group2));

        TurRole role1 = mock(TurRole.class);
        when(role1.getName()).thenReturn("ROLE_ADMIN");
        when(role1.getTurPrivileges()).thenReturn(Collections.emptyList());

        TurRole role2 = mock(TurRole.class);
        when(role2.getName()).thenReturn("ROLE_EDITOR");
        when(role2.getTurPrivileges()).thenReturn(Collections.emptyList());

        when(turRoleRepository.findByTurGroupsContaining(group1)).thenReturn(Set.of(role1));
        when(turRoleRepository.findByTurGroupsContaining(group2)).thenReturn(Set.of(role2));

        UserDetails details = service.loadUserByUsername("multiuser");

        assertThat(details.getAuthorities())
                .extracting(a -> a.getAuthority())
                .contains("ROLE_ADMIN", "ROLE_EDITOR");
    }

    @Test
    void shouldReturnAccountAlwaysNonExpiredAndEnabled() {
        when(turConfigProperties.isPermissions()).thenReturn(false);

        TurUser user = TurUser.builder().username("activeuser").password("pass").build();
        when(turUserRepository.findByUsername("activeuser")).thenReturn(user);
        when(turPrivilegeRepository.findAll()).thenReturn(Collections.emptyList());

        UserDetails details = service.loadUserByUsername("activeuser");

        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void shouldPreservePasswordFromUser() {
        when(turConfigProperties.isPermissions()).thenReturn(false);

        TurUser user = TurUser.builder().username("passuser").password("encoded-password").build();
        when(turUserRepository.findByUsername("passuser")).thenReturn(user);
        when(turPrivilegeRepository.findAll()).thenReturn(Collections.emptyList());

        UserDetails details = service.loadUserByUsername("passuser");

        assertThat(details.getPassword()).isEqualTo("encoded-password");
    }

    @Test
    void shouldCallFindByUsernameOnRepository() {
        when(turUserRepository.findByUsername("checkuser")).thenReturn(null);

        assertThatThrownBy(() -> service.loadUserByUsername("checkuser"))
                .isInstanceOf(UsernameNotFoundException.class);

        verify(turUserRepository).findByUsername("checkuser");
    }

    @Test
    void allAuthoritiesShouldIncludeRoleAdminWhenPermissionsDisabledAndNoPrivileges() {
        when(turConfigProperties.isPermissions()).thenReturn(false);

        TurUser user = TurUser.builder().username("noprivs").password("pass").build();
        when(turUserRepository.findByUsername("noprivs")).thenReturn(user);
        when(turPrivilegeRepository.findAll()).thenReturn(Collections.emptyList());

        UserDetails details = service.loadUserByUsername("noprivs");

        assertThat(details.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_ADMIN");
    }
}
