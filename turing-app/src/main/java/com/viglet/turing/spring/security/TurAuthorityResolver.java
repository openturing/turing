package com.viglet.turing.spring.security;

import com.viglet.core.security.VigletKeycloakClaims;
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
import com.viglet.turing.properties.TurTenancyProperty;
import com.viglet.core.tenancy.VigletPlatformAdminService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Map;
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

    @Transactional(readOnly = true)
    public void resolve(String username, Set<GrantedAuthority> authorities) {
        resolve(username, Set.of(), authorities);
    }

    /**
     * T366 / §XIV.8.5 — same as {@link #resolve(String, Set)} but also maps a
     * Keycloak <em>realm</em> role to {@code ROLE_PLATFORM_ADMIN}. The
     * {@code realmRoles} come from the principal's {@code realm_access.roles}
     * claim (extract with {@link #extractRealmRoles(Map)} on the OIDC/OAuth2
     * paths). When the realm role isn't available (e.g. local session login),
     * pass an empty set — the bootstrap admin can still be granted via
     * {@code turing.tenancy.admin-implies-platform-admin}.
     */
    @Transactional(readOnly = true)
    public void resolve(String username, Set<String> realmRoles, Set<GrantedAuthority> authorities) {
        if (!turConfigProperties.isPermissions()) {
            grantAllAuthorities(authorities);
        } else if (username != null) {
            resolveFromDatabase(username, authorities);
        }
        tenantAuthorities(username)
                .forEach(a -> authorities.add(new SimpleGrantedAuthority(a)));
        if (isPlatformAdmin(username, realmRoles)) {
            authorities.add(new SimpleGrantedAuthority(VigletPlatformAdminService.ROLE_PLATFORM_ADMIN));
        }
    }

    /**
     * T366 / §XIV.8.5 — decides whether a principal holds the platform-admin
     * authority: either its Keycloak realm roles include the configured
     * {@code turing.tenancy.platform-admin-role}, or it is the bootstrap admin
     * ({@code turing.keycloak-admin-id}) and
     * {@code turing.tenancy.admin-implies-platform-admin} is on.
     */
    private boolean isPlatformAdmin(String username, Set<String> realmRoles) {
        TurTenancyProperty tenancy = turConfigProperties.getTenancy();
        if (tenancy == null) {
            return false;
        }
        String roleName = tenancy.getPlatformAdminRole();
        if (StringUtils.hasText(roleName) && realmRoles != null && realmRoles.contains(roleName)) {
            return true;
        }
        return tenancy.isAdminImpliesPlatformAdmin()
                && username != null
                && username.equals(turConfigProperties.getKeycloakAdminId());
    }

    /**
     * T366 / §XIV.8.5 — extract the Keycloak realm roles from a claims/attributes
     * map: the {@code realm_access.roles} array Keycloak puts in the token but
     * which Spring Security does not map to authorities by default. Returns an
     * empty set when the claim is absent or malformed. Shared by the OIDC
     * ({@code OidcUser.getClaims()}) and OAuth2 ({@code OAuth2User.getAttributes()})
     * paths.
     *
     * <p>T369 / Block Q — the parsing itself now lives in {@code viglet-core-security}
     * ({@link VigletKeycloakClaims}); this method stays as Turing's stable entry
     * point for the OIDC/OAuth2 callers.
     */
    public static Set<String> extractRealmRoles(Map<String, Object> claims) {
        return VigletKeycloakClaims.extractRealmRoles(claims);
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

    // Traverses the lazy role → turPrivileges collection, so it must run inside
    // the read-only transaction opened by the public resolve() methods; otherwise
    // role.getTurPrivileges() throws LazyInitializationException (no session).
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
