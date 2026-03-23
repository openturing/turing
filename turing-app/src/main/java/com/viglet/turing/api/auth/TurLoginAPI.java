package com.viglet.turing.api.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.bean.TurCurrentUser;
import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurPrivilegeRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.properties.TurConfigProperties;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@RestController
@RequestMapping("/api/login")
@Tag(name = "Login", description = "Session-based authentication")
public class TurLoginAPI {

    private static final String ADMINISTRATOR = "Administrator";

    private final AuthenticationManager authenticationManager;
    private final TurUserRepository turUserRepository;
    private final TurGroupRepository turGroupRepository;
    private final TurRoleRepository turRoleRepository;
    private final TurPrivilegeRepository turPrivilegeRepository;
    private final TurConfigProperties turConfigProperties;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public TurLoginAPI(AuthenticationManager authenticationManager,
            TurUserRepository turUserRepository,
            TurGroupRepository turGroupRepository,
            TurRoleRepository turRoleRepository,
            TurPrivilegeRepository turPrivilegeRepository,
            TurConfigProperties turConfigProperties) {
        this.authenticationManager = authenticationManager;
        this.turUserRepository = turUserRepository;
        this.turGroupRepository = turGroupRepository;
        this.turRoleRepository = turRoleRepository;
        this.turPrivilegeRepository = turPrivilegeRepository;
        this.turConfigProperties = turConfigProperties;
    }

    public record LoginRequest(String username, String password) {
    }

    @PostMapping
    public ResponseEntity<TurCurrentUser> login(@RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        TurUser turUser = turUserRepository.findByUsername(request.username());
        boolean isAdmin = !turConfigProperties.isPermissions();
        if (!isAdmin && turUser.getTurGroups() != null) {
            for (TurGroup turGroup : turUser.getTurGroups()) {
                if (turGroup.getName().equals(ADMINISTRATOR)) {
                    isAdmin = true;
                    break;
                }
            }
        }

        TurCurrentUser currentUser = new TurCurrentUser();
        currentUser.setUsername(turUser.getUsername());
        currentUser.setFirstName(turUser.getFirstName());
        currentUser.setLastName(turUser.getLastName());
        currentUser.setAdmin(isAdmin);
        currentUser.setEmail(turUser.getEmail());
        currentUser.setAvatarUrl(turUser.getAvatarUrl());
        currentUser.setHasAvatar(turUser.getAvatarUrl() != null);
        currentUser.setPrivileges(resolvePrivileges(turUser));

        return ResponseEntity.ok(currentUser);
    }

    private List<String> resolvePrivileges(TurUser turUser) {
        if (!turConfigProperties.isPermissions()) {
            return turPrivilegeRepository.findAll().stream()
                    .map(com.viglet.turing.persistence.model.auth.TurPrivilege::getName)
                    .toList();
        }
        var groups = turGroupRepository.findByTurUsersContaining(turUser);
        var privileges = new LinkedHashSet<String>();
        for (var group : groups) {
            for (var role : turRoleRepository.findByTurGroupsContaining(group)) {
                for (var privilege : role.getTurPrivileges()) {
                    privileges.add(privilege.getName());
                }
            }
        }
        return new ArrayList<>(privileges);
    }
}
