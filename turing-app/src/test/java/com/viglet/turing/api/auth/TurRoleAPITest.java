package com.viglet.turing.api.auth;

import static org.mockito.ArgumentMatchers.any;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.persistence.mapper.auth.TurRoleMapper;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;

@ExtendWith(MockitoExtension.class)
class TurRoleAPITest {

    private MockMvc mockMvc;

    @Mock
    private TurRoleRepository turRoleRepository;

    @Spy
    private TurRoleMapper turRoleMapper = Mappers.getMapper(TurRoleMapper.class);

    @InjectMocks
    private TurRoleAPI turRoleAPI;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(turRoleAPI).build();
    }

    @Test
    void testTurRoleList() throws Exception {
        TurRole role1 = new TurRole();
        role1.setId("1");
        role1.setName("Admin");

        TurRole role2 = new TurRole();
        role2.setId("2");
        role2.setName("User");

        List<TurRole> roles = Arrays.asList(role1, role2);
        when(turRoleRepository.findAll()).thenReturn(roles);

        mockMvc.perform(get("/api/v2/role"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].name").value("Admin"))
                .andExpect(jsonPath("$[1].id").value("2"))
                .andExpect(jsonPath("$[1].name").value("User"));

        verify(turRoleRepository, times(1)).findAll();
    }

    @Test
    void testTurRoleEdit_Found() throws Exception {
        TurRole role = new TurRole();
        role.setId("1");
        role.setName("Admin");

        when(turRoleRepository.findById("1")).thenReturn(Optional.of(role));

        mockMvc.perform(get("/api/v2/role/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.name").value("Admin"));

        verify(turRoleRepository, times(1)).findById("1");
    }

    @Test
    void testTurRoleEdit_NotFound() throws Exception {
        when(turRoleRepository.findById("1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v2/role/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(turRoleRepository, times(1)).findById("1");
    }

    @Test
    void testTurRoleUpdate() throws Exception {
        TurRole role = new TurRole();
        role.setId("1");
        role.setName("Updated Role");

        when(turRoleRepository.save(any(TurRole.class))).thenReturn(role);

        mockMvc.perform(put("/api/v2/role/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Role"));

        verify(turRoleRepository, times(1)).save(any(TurRole.class));
    }

    @Test
    void testTurRoleDelete() throws Exception {
        mockMvc.perform(delete("/api/v2/role/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(turRoleRepository, times(1)).deleteById("1");
    }

    @Test
    void testTurRoleAdd() throws Exception {
        TurRole role = new TurRole();
        role.setName("New Role");

        when(turRoleRepository.save(any(TurRole.class))).thenReturn(role);

        mockMvc.perform(post("/api/v2/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Role"));

        verify(turRoleRepository, times(1)).save(any(TurRole.class));
    }

    @Test
    void testTurRoleStructure() throws Exception {
        mockMvc.perform(get("/api/v2/role/model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());
    }
}
