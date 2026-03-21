package com.viglet.turing.spring.security;

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Enriches Keycloak OAuth2 users with authorities derived from their
 * local database group → role assignments.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.17
 */
@Service
public class TurOAuth2UserService extends DefaultOAuth2UserService {

    private static final String PREFERRED_USERNAME = "preferred_username";

    private final TurUserRepository turUserRepository;
    private final TurGroupRepository turGroupRepository;
    private final TurRoleRepository turRoleRepository;

    public TurOAuth2UserService(TurUserRepository turUserRepository,
                                TurGroupRepository turGroupRepository,
                                TurRoleRepository turRoleRepository) {
        this.turUserRepository = turUserRepository;
        this.turGroupRepository = turGroupRepository;
        this.turRoleRepository = turRoleRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String username = oAuth2User.getAttribute(PREFERRED_USERNAME);

        Set<GrantedAuthority> authorities = new HashSet<>(oAuth2User.getAuthorities());

        if (username != null) {
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

        return new DefaultOAuth2User(authorities, oAuth2User.getAttributes(),
                userRequest.getClientRegistration().getProviderDetails()
                        .getUserInfoEndpoint().getUserNameAttributeName());
    }
}
