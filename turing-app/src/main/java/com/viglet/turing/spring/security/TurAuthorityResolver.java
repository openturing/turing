package com.viglet.turing.spring.security;

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurPrivilegeRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.properties.TurConfigProperties;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

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
    private final TurConfigProperties turConfigProperties;

    public TurAuthorityResolver(TurUserRepository turUserRepository,
                                TurGroupRepository turGroupRepository,
                                TurRoleRepository turRoleRepository,
                                TurPrivilegeRepository turPrivilegeRepository,
                                TurConfigProperties turConfigProperties) {
        this.turUserRepository = turUserRepository;
        this.turGroupRepository = turGroupRepository;
        this.turRoleRepository = turRoleRepository;
        this.turPrivilegeRepository = turPrivilegeRepository;
        this.turConfigProperties = turConfigProperties;
    }

    public void resolve(String username, Set<GrantedAuthority> authorities) {
        if (!turConfigProperties.isPermissions()) {
            grantAllAuthorities(authorities);
        } else if (username != null) {
            resolveFromDatabase(username, authorities);
        }
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
