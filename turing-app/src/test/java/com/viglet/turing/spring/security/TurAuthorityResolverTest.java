package com.viglet.turing.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

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
 * Tests for TurAuthorityResolver.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurAuthorityResolverTest {

    @Mock
    private TurUserRepository turUserRepository;
    @Mock
    private TurGroupRepository turGroupRepository;
    @Mock
    private TurRoleRepository turRoleRepository;
    @Mock
    private TurPrivilegeRepository turPrivilegeRepository;
    @Mock
    private com.viglet.turing.persistence.repository.tenant.TurTenantMembershipRepository turTenantMembershipRepository;
    @Mock
    private TurConfigProperties turConfigProperties;
    @InjectMocks
    private TurAuthorityResolver resolver;

    @org.junit.jupiter.api.Test
    void tenantAuthoritiesAddsMembershipAndRoleWhenTenancyEnabled() {
        com.viglet.turing.properties.TurTenancyProperty tenancy =
                new com.viglet.turing.properties.TurTenancyProperty();
        tenancy.setEnabled(true);
        when(turConfigProperties.getTenancy()).thenReturn(tenancy);

        com.viglet.turing.persistence.model.tenant.TurTenant tenant =
                new com.viglet.turing.persistence.model.tenant.TurTenant();
        tenant.setId("t-1");
        com.viglet.turing.persistence.model.tenant.TurTenantMembership m =
                new com.viglet.turing.persistence.model.tenant.TurTenantMembership();
        m.setTenant(tenant);
        m.setRole(com.viglet.turing.persistence.model.tenant.TurTenantRole.OWNER);
        m.setStatus(com.viglet.turing.persistence.model.tenant.TurTenantMembershipStatus.ACTIVE);
        when(turTenantMembershipRepository.findByUsername("alice")).thenReturn(List.of(m));

        assertThat(resolver.tenantAuthorities("alice"))
                .containsExactlyInAnyOrder("TENANT_t-1", "TENANT_t-1_OWNER");
    }

    @org.junit.jupiter.api.Test
    void tenantAuthoritiesEmptyWhenTenancyDisabled() {
        com.viglet.turing.properties.TurTenancyProperty tenancy =
                new com.viglet.turing.properties.TurTenancyProperty();
        when(turConfigProperties.getTenancy()).thenReturn(tenancy); // enabled=false
        assertThat(resolver.tenantAuthorities("alice")).isEmpty();
        verifyNoInteractions(turTenantMembershipRepository);
    }

    @Test
    void shouldGrantAllAuthoritiesWhenPermissionsDisabled() {
        when(turConfigProperties.isPermissions()).thenReturn(false);

        TurPrivilege priv = new TurPrivilege("PRIV_INDEX");
        when(turPrivilegeRepository.findAll()).thenReturn(List.of(priv));

        Set<GrantedAuthority> authorities = new HashSet<>();
        resolver.resolve("anyuser", authorities);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN", "PRIV_INDEX");
    }

    @Test
    void shouldNotQueryDatabaseWhenPermissionsDisabled() {
        when(turConfigProperties.isPermissions()).thenReturn(false);
        when(turPrivilegeRepository.findAll()).thenReturn(Collections.emptyList());

        Set<GrantedAuthority> authorities = new HashSet<>();
        resolver.resolve("someuser", authorities);

        verifyNoInteractions(turUserRepository);
        verifyNoInteractions(turGroupRepository);
        verifyNoInteractions(turRoleRepository);
    }

    @Test
    void shouldResolveFromDatabaseWhenPermissionsEnabled() {
        when(turConfigProperties.isPermissions()).thenReturn(true);

        TurUser user = TurUser.builder().username("testuser").password("pass").build();
        when(turUserRepository.findByUsername("testuser")).thenReturn(user);

        TurGroup group = mock(TurGroup.class);
        when(turGroupRepository.findByTurUsersContaining(user)).thenReturn(Set.of(group));

        TurPrivilege privilege = new TurPrivilege("PRIV_SEARCH");
        TurRole role = mock(TurRole.class);
        when(role.getName()).thenReturn("ROLE_EDITOR");
        when(role.getTurPrivileges()).thenReturn(List.of(privilege));
        when(turRoleRepository.findByTurGroupsContaining(group)).thenReturn(Set.of(role));

        Set<GrantedAuthority> authorities = new HashSet<>();
        resolver.resolve("testuser", authorities);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_EDITOR", "PRIV_SEARCH");
    }

    @Test
    void shouldDoNothingWhenPermissionsEnabledAndUsernameIsNull() {
        when(turConfigProperties.isPermissions()).thenReturn(true);

        Set<GrantedAuthority> authorities = new HashSet<>();
        resolver.resolve(null, authorities);

        assertThat(authorities).isEmpty();
        verifyNoInteractions(turUserRepository);
    }

    @Test
    void shouldDoNothingWhenUserNotFoundInDatabase() {
        when(turConfigProperties.isPermissions()).thenReturn(true);
        when(turUserRepository.findByUsername("ghost")).thenReturn(null);

        Set<GrantedAuthority> authorities = new HashSet<>();
        resolver.resolve("ghost", authorities);

        assertThat(authorities).isEmpty();
    }

    @Test
    void shouldHandleUserWithNoGroups() {
        when(turConfigProperties.isPermissions()).thenReturn(true);

        TurUser user = TurUser.builder().username("loneuser").password("pass").build();
        when(turUserRepository.findByUsername("loneuser")).thenReturn(user);
        when(turGroupRepository.findByTurUsersContaining(user)).thenReturn(Collections.emptySet());

        Set<GrantedAuthority> authorities = new HashSet<>();
        resolver.resolve("loneuser", authorities);

        assertThat(authorities).isEmpty();
    }

    @Test
    void shouldPreserveExistingAuthorities() {
        when(turConfigProperties.isPermissions()).thenReturn(false);
        when(turPrivilegeRepository.findAll()).thenReturn(Collections.emptyList());

        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority("EXISTING_AUTHORITY"));

        resolver.resolve("user", authorities);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains("EXISTING_AUTHORITY", "ROLE_ADMIN");
    }

    @Test
    void shouldHandleMultipleGroupsAndRolesWithPrivileges() {
        when(turConfigProperties.isPermissions()).thenReturn(true);

        TurUser user = TurUser.builder().username("poweruser").password("pass").build();
        when(turUserRepository.findByUsername("poweruser")).thenReturn(user);

        TurGroup group1 = mock(TurGroup.class);
        TurGroup group2 = mock(TurGroup.class);
        when(turGroupRepository.findByTurUsersContaining(user)).thenReturn(Set.of(group1, group2));

        TurPrivilege priv1 = new TurPrivilege("PRIV_A");
        TurPrivilege priv2 = new TurPrivilege("PRIV_B");

        TurRole role1 = mock(TurRole.class);
        when(role1.getName()).thenReturn("ROLE_ADMIN");
        when(role1.getTurPrivileges()).thenReturn(List.of(priv1));

        TurRole role2 = mock(TurRole.class);
        when(role2.getName()).thenReturn("ROLE_VIEWER");
        when(role2.getTurPrivileges()).thenReturn(List.of(priv2));

        when(turRoleRepository.findByTurGroupsContaining(group1)).thenReturn(Set.of(role1));
        when(turRoleRepository.findByTurGroupsContaining(group2)).thenReturn(Set.of(role2));

        Set<GrantedAuthority> authorities = new HashSet<>();
        resolver.resolve("poweruser", authorities);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN", "ROLE_VIEWER", "PRIV_A", "PRIV_B");
    }

    @Test
    void shouldGrantOnlyRoleAdminWhenNoPrivilegesExistAndPermissionsDisabled() {
        when(turConfigProperties.isPermissions()).thenReturn(false);
        when(turPrivilegeRepository.findAll()).thenReturn(Collections.emptyList());

        Set<GrantedAuthority> authorities = new HashSet<>();
        resolver.resolve("user", authorities);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }
}
