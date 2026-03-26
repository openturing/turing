package com.viglet.turing.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.turing.bean.TurCurrentUser;
import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurPrivilege;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurPrivilegeRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.properties.TurConfigProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Tests for TurLoginAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurLoginAPITest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private TurUserRepository turUserRepository;
    @Mock
    private TurGroupRepository turGroupRepository;
    @Mock
    private TurRoleRepository turRoleRepository;
    @Mock
    private TurPrivilegeRepository turPrivilegeRepository;
    @Mock
    private TurConfigProperties turConfigProperties;

    private TurLoginAPI loginAPI;

    @BeforeEach
    void setUp() {
        loginAPI = new TurLoginAPI(authenticationManager, turUserRepository, turGroupRepository,
                turRoleRepository, turPrivilegeRepository, turConfigProperties);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginShouldReturnCurrentUserOnSuccess() {
        var request = new TurLoginAPI.LoginRequest("testuser", "password");
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "testuser", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        when(httpRequest.getSession(true)).thenReturn(session);

        TurUser turUser = new TurUser();
        turUser.setUsername("testuser");
        turUser.setFirstName("Test");
        turUser.setLastName("User");
        turUser.setEmail("test@example.com");
        when(turUserRepository.findByUsername("testuser")).thenReturn(turUser);
        when(turConfigProperties.isPermissions()).thenReturn(false);

        TurPrivilege priv = new TurPrivilege();
        priv.setName("VIEW_ALL");
        when(turPrivilegeRepository.findAll()).thenReturn(java.util.List.of(priv));

        ResponseEntity<TurCurrentUser> response = loginAPI.login(request, httpRequest, httpResponse);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUsername()).isEqualTo("testuser");
        assertThat(response.getBody().getFirstName()).isEqualTo("Test");
        assertThat(response.getBody().getLastName()).isEqualTo("User");
        assertThat(response.getBody().getEmail()).isEqualTo("test@example.com");
        assertThat(response.getBody().isAdmin()).isTrue(); // permissions=false -> all privileges
    }

    @Test
    void loginShouldSetAdminWhenUserInAdministratorGroup() {
        var request = new TurLoginAPI.LoginRequest("admin", "adminpass");
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin", "adminpass",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(httpRequest.getSession(true)).thenReturn(session);
        when(turConfigProperties.isPermissions()).thenReturn(true);

        TurGroup adminGroup = new TurGroup();
        adminGroup.setName("Administrator");

        TurUser turUser = new TurUser();
        turUser.setUsername("admin");
        turUser.setTurGroups(new HashSet<>(Set.of(adminGroup)));
        when(turUserRepository.findByUsername("admin")).thenReturn(turUser);

        TurRole role = mock(TurRole.class);
        TurPrivilege priv = new TurPrivilege();
        priv.setName("ADMIN_ALL");
        when(role.getTurPrivileges()).thenReturn(java.util.List.of(priv));
        when(turGroupRepository.findByTurUsersContaining(turUser)).thenReturn(Set.of(adminGroup));
        when(turRoleRepository.findByTurGroupsContaining(adminGroup)).thenReturn(Set.of(role));

        ResponseEntity<TurCurrentUser> response = loginAPI.login(request, httpRequest, httpResponse);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isAdmin()).isTrue();
        assertThat(response.getBody().getPrivileges()).contains("ADMIN_ALL");
    }

    @Test
    void loginShouldNotSetAdminWhenUserNotInAdminGroup() {
        var request = new TurLoginAPI.LoginRequest("user", "pass");
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", "pass",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(httpRequest.getSession(true)).thenReturn(session);
        when(turConfigProperties.isPermissions()).thenReturn(true);

        TurGroup regularGroup = new TurGroup();
        regularGroup.setName("Users");

        TurUser turUser = new TurUser();
        turUser.setUsername("user");
        turUser.setTurGroups(new HashSet<>(Set.of(regularGroup)));
        when(turUserRepository.findByUsername("user")).thenReturn(turUser);
        when(turGroupRepository.findByTurUsersContaining(turUser)).thenReturn(Set.of(regularGroup));
        when(turRoleRepository.findByTurGroupsContaining(regularGroup)).thenReturn(Collections.emptySet());

        ResponseEntity<TurCurrentUser> response = loginAPI.login(request, httpRequest, httpResponse);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isAdmin()).isFalse();
    }

    @Test
    void loginShouldHandleUserWithNullGroups() {
        var request = new TurLoginAPI.LoginRequest("user", "pass");
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", "pass",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(httpRequest.getSession(true)).thenReturn(session);
        when(turConfigProperties.isPermissions()).thenReturn(true);

        TurUser turUser = new TurUser();
        turUser.setUsername("user");
        turUser.setTurGroups(null);
        when(turUserRepository.findByUsername("user")).thenReturn(turUser);
        when(turGroupRepository.findByTurUsersContaining(turUser)).thenReturn(Collections.emptySet());

        ResponseEntity<TurCurrentUser> response = loginAPI.login(request, httpRequest, httpResponse);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isAdmin()).isFalse();
    }

    @Test
    void loginShouldSetAvatarUrl() {
        var request = new TurLoginAPI.LoginRequest("user", "pass");
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", "pass",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(httpRequest.getSession(true)).thenReturn(session);
        when(turConfigProperties.isPermissions()).thenReturn(false);

        TurUser turUser = new TurUser();
        turUser.setUsername("user");
        turUser.setAvatarUrl("https://example.com/avatar.png");
        when(turUserRepository.findByUsername("user")).thenReturn(turUser);
        when(turPrivilegeRepository.findAll()).thenReturn(Collections.emptyList());

        ResponseEntity<TurCurrentUser> response = loginAPI.login(request, httpRequest, httpResponse);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(response.getBody().isHasAvatar()).isTrue();
    }

    @Test
    void loginShouldSetHasAvatarFalseWhenNull() {
        var request = new TurLoginAPI.LoginRequest("user", "pass");
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", "pass",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(httpRequest.getSession(true)).thenReturn(session);
        when(turConfigProperties.isPermissions()).thenReturn(false);

        TurUser turUser = new TurUser();
        turUser.setUsername("user");
        turUser.setAvatarUrl(null);
        when(turUserRepository.findByUsername("user")).thenReturn(turUser);
        when(turPrivilegeRepository.findAll()).thenReturn(Collections.emptyList());

        ResponseEntity<TurCurrentUser> response = loginAPI.login(request, httpRequest, httpResponse);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isHasAvatar()).isFalse();
    }

    // --- Record Tests ---

    @Test
    void loginRequestRecord() {
        var req = new TurLoginAPI.LoginRequest("user", "pass");
        assertThat(req.username()).isEqualTo("user");
        assertThat(req.password()).isEqualTo("pass");
    }
}
