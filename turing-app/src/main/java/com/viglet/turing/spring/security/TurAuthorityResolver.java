package com.viglet.turing.spring.security;

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurPrivilegeRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.persistence.model.tenant.TurTenantMembership;
import com.viglet.turing.persistence.model.tenant.TurTenantMembershipStatus;
import com.viglet.turing.persistence.repository.tenant.TurTenantMembershipRepository;
import com.viglet.turing.properties.TurConfigProperties;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Centralizes authority resolution for all authentication flows.
 * When permissions are disabled, grants ROLE_ADMIN and all privileges.
 * When enabled, resolves from user → groups → roles → privileges.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Component
public class TurAuthorityResolver {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final TurUserRepository turUserRepository;
    private final TurGroupRepository turGroupRepository;
    private final TurRoleRepository turRoleRepository;
    private final TurPrivilegeRepository turPrivilegeRepository;
    private final TurTenantMembershipRepository turTenantMembershipRepository;
    private final TurConfigProperties turConfigProperties;

    public TurAuthorityResolver(TurUserRepository turUserRepository,
                                TurGroupRepository turGroupRepository,
                                TurRoleRepository turRoleRepository,
                                TurPrivilegeRepository turPrivilegeRepository,
                                TurTenantMembershipRepository turTenantMembershipRepository,
                                TurConfigProperties turConfigProperties) {
        this.turUserRepository = turUserRepository;
        this.turGroupRepository = turGroupRepository;
        this.turRoleRepository = turRoleRepository;
        this.turPrivilegeRepository = turPrivilegeRepository;
        this.turTenantMembershipRepository = turTenantMembershipRepository;
        this.turConfigProperties = turConfigProperties;
    }

    public void resolve(String username, Set<GrantedAuthority> authorities) {
        if (!turConfigProperties.isPermissions()) {
            grantAllAuthorities(authorities);
        } else if (username != null) {
            resolveFromDatabase(username, authorities);
        }
        tenantAuthorities(username)
                .forEach(a -> authorities.add(new SimpleGrantedAuthority(a)));
    }

    /**
     * T263 / §XIV.3.1 — per-tenant authorities for {@code username}: a
     * {@code TENANT_<tenantId>} membership marker plus a
     * {@code TENANT_<tenantId>_<ROLE>} role authority for every <em>active</em>
     * {@link TurTenantMembership}. Empty when tenancy is off, so single-tenant
     * installs gain no extra authorities. Shared by the OIDC/OAuth2 and session
     * authentication paths.
     */
    public Set<String> tenantAuthorities(String username) {
        Set<String> authorities = new HashSet<>();
        if (username == null || turConfigProperties.getTenancy() == null
                || !turConfigProperties.getTenancy().isEnabled()) {
            return authorities;
        }
        for (TurTenantMembership membership : turTenantMembershipRepository.findByUsername(username)) {
            if (membership.getStatus() != TurTenantMembershipStatus.ACTIVE) {
                continue;
            }
            String tenantId = membership.getTenant().getId();
            authorities.add("TENANT_" + tenantId);
            authorities.add("TENANT_" + tenantId + "_" + membership.getRole().name());
        }
        return authorities;
    }

    private void grantAllAuthorities(Set<GrantedAuthority> authorities) {
        authorities.add(new SimpleGrantedAuthority(ROLE_ADMIN));
        turPrivilegeRepository.findAll()
                .forEach(p -> authorities.add(new SimpleGrantedAuthority(p.getName())));
    }

    private void resolveFromDatabase(String username, Set<GrantedAuthority> authorities) {
        TurUser turUser = turUserRepository.findByUsername(username);
        if (turUser != null) {
            Set<TurGroup> groups = turGroupRepository.findByTurUsersContaining(turUser);
            for (TurGroup group : groups) {
                Set<TurRole> roles = turRoleRepository.findByTurGroupsContaining(group);
                for (TurRole role : roles) {
                    authorities.add(new SimpleGrantedAuthority(role.getName()));
                    for (var privilege : role.getTurPrivileges()) {
                        authorities.add(new SimpleGrantedAuthority(privilege.getName()));
                    }
                }
            }
        }
    }
}
