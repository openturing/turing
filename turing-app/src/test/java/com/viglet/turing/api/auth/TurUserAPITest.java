package com.viglet.turing.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.HashSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.persistence.mapper.auth.TurUserMapper;
import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.properties.TurConfigProperties;

@ExtendWith(MockitoExtension.class)
class TurUserAPITest {

    private MockMvc mockMvc;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TurUserRepository turUserRepository;

    @Mock
    private TurGroupRepository turGroupRepository;

    @Mock
    private TurConfigProperties turConfigProperties;

    @Spy
    private TurUserMapper turUserMapper = Mappers.getMapper(TurUserMapper.class);

    @InjectMocks
    private TurUserAPI turUserAPI;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(turUserAPI).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testTurUserList() throws Exception {
        TurUser user = new TurUser();
        user.setUsername("testuser");
        when(turUserRepository.findAll()).thenReturn(Collections.singletonList(user));

        mockMvc.perform(get("/api/v2/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("testuser"));

        verify(turUserRepository, times(1)).findAll();
    }

    @Test
    void testTurUserCurrentWithAnonymousUser() throws Exception {
        Authentication auth = new AnonymousAuthenticationToken("key", "anonymousUser",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        mockMvc.perform(get("/api/v2/user/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").doesNotExist());
    }

    @Test
    void testTurUserCurrentWithRegularUser() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken("testuser", "password");
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        when(turConfigProperties.isKeycloak()).thenReturn(false);

        TurUser turUser = new TurUser();
        turUser.setUsername("testuser");
        turUser.setFirstName("Test");
        turUser.setLastName("User");
        turUser.setPassword("nopass");

        TurGroup group = new TurGroup();
        group.setName("Administrator");
        turUser.setTurGroups(new HashSet<>(Collections.singletonList(group)));

        when(turUserRepository.findByUsername("testuser")).thenReturn(turUser);

        mockMvc.perform(get("/api/v2/user/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.firstName").value("Test"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.admin").value(true));
    }

    @Test
    void testTurUserCurrentWithKeycloakUser() throws Exception {
        OAuth2User oauth2User = mock(OAuth2User.class);
        when(oauth2User.getAttribute("preferred_username")).thenReturn("kcuser");
        when(oauth2User.getAttribute("given_name")).thenReturn("KC");
        when(oauth2User.getAttribute("family_name")).thenReturn("User");
        when(oauth2User.getAttribute("email")).thenReturn("kc@example.com");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauth2User);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        when(turConfigProperties.isKeycloak()).thenReturn(true);

        mockMvc.perform(get("/api/v2/user/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("kcuser"))
                .andExpect(jsonPath("$.firstName").value("KC"))
                .andExpect(jsonPath("$.admin").value(true));
    }

    @Test
    void testTurUserEdit_Found() throws Exception {
        TurUser user = new TurUser();
        user.setUsername("testuser");

        when(turUserRepository.findByUsername("testuser")).thenReturn(user);
        when(turGroupRepository.findByTurUsersContaining(user)).thenReturn(new HashSet<>());

        mockMvc.perform(get("/api/v2/user/testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    void testTurUserEdit_NotFound() throws Exception {
        when(turUserRepository.findByUsername("nonexistent")).thenReturn(null);

        mockMvc.perform(get("/api/v2/user/nonexistent"))
                .andExpect(status().isOk()) // The API returns a new TurUser
                .andExpect(jsonPath("$.username").doesNotExist());
    }

    @Test
    void testTurUserUpdate_Found() throws Exception {
        TurUser userEdit = new TurUser();
        userEdit.setUsername("testuser");

        TurUser updatedInfo = new TurUser();
        updatedInfo.setFirstName("New");
        updatedInfo.setLastName("Name");
        updatedInfo.setPassword("newpass");

        when(turUserRepository.findByUsername("testuser")).thenReturn(userEdit);
        when(passwordEncoder.encode("newpass")).thenReturn("encodedpass");

        mockMvc.perform(put("/api/v2/user/testuser")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedInfo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("New"));

        verify(turUserRepository, times(1)).save(userEdit);
        verify(passwordEncoder, times(1)).encode("newpass");
    }

    @Test
    void testTurUserDelete_NotAdmin() throws Exception {
        mockMvc.perform(delete("/api/v2/user/testuser"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(turUserRepository, times(1)).deleteByUsername("testuser");
    }

    @Test
    void testTurUserDelete_Admin() throws Exception {
        mockMvc.perform(delete("/api/v2/user/admin"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));

        verify(turUserRepository, never()).deleteByUsername("admin");
    }

    @Test
    void testTurUserAdd() throws Exception {
        TurUser newUser = new TurUser();
        newUser.setUsername("newuser");
        newUser.setPassword("password");

        when(passwordEncoder.encode("password")).thenReturn("encoded");

        mockMvc.perform(post("/api/v2/user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newuser"));

        verify(turUserRepository, times(1)).save(any(TurUser.class));
    }

    @Test
    void testTurUserStructure() throws Exception {
        mockMvc.perform(get("/api/v2/user/model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").doesNotExist());
    }
}
