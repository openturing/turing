package com.viglet.turing.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
import com.viglet.turing.persistence.mapper.auth.TurGroupMapper;
import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;

@ExtendWith(MockitoExtension.class)
class TurGroupAPITest {

    private MockMvc mockMvc;

    @Mock
    private TurGroupRepository turGroupRepository;

    @Mock
    private TurUserRepository turUserRepository;

    @Spy
    private TurGroupMapper turGroupMapper = Mappers.getMapper(TurGroupMapper.class);

    @InjectMocks
    private TurGroupAPI turGroupAPI;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(turGroupAPI).build();
    }

    @Test
    void testTurGroupList() throws Exception {
        TurGroup group1 = new TurGroup();
        group1.setId("1");
        group1.setName("Admin Group");

        TurGroup group2 = new TurGroup();
        group2.setId("2");
        group2.setName("User Group");

        List<TurGroup> groups = Arrays.asList(group1, group2);
        when(turGroupRepository.findAll()).thenReturn(groups);

        mockMvc.perform(get("/api/v2/group"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].name").value("Admin Group"))
                .andExpect(jsonPath("$[1].id").value("2"))
                .andExpect(jsonPath("$[1].name").value("User Group"));

        verify(turGroupRepository, times(1)).findAll();
    }

    @Test
    void testTurGroupEdit_Found() throws Exception {
        TurGroup group = new TurGroup();
        group.setId("1");
        group.setName("Admin Group");

        TurUser user = new TurUser();
        user.setUsername("testuser");
        Set<TurUser> users = new HashSet<>(Collections.singletonList(user));

        when(turGroupRepository.findById("1")).thenReturn(Optional.of(group));
        when(turUserRepository.findByTurGroupsIn(anyList())).thenReturn(users);

        mockMvc.perform(get("/api/v2/group/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.name").value("Admin Group"))
                .andExpect(jsonPath("$.turUsers[0].username").value("testuser"));

        verify(turGroupRepository, times(1)).findById("1");
        verify(turUserRepository, times(1)).findByTurGroupsIn(anyList());
    }

    @Test
    void testTurGroupEdit_NotFound() throws Exception {
        when(turGroupRepository.findById("1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v2/group/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(turGroupRepository, times(1)).findById("1");
        verify(turUserRepository, never()).findByTurGroupsIn(anyList());
    }

    @Test
    void testTurGroupUpdate_Found() throws Exception {
        TurGroup group = new TurGroup();
        group.setId("1");
        group.setName("Admin Group");

        TurUser existingUser = new TurUser();
        existingUser.setUsername("oldUser");
        existingUser.setTurGroups(new HashSet<>(Collections.singletonList(group)));

        TurUser newUser = new TurUser();
        newUser.setUsername("newUser");
        newUser.setTurGroups(new HashSet<>());

        group.setTurUsers(new HashSet<>(Collections.singletonList(newUser)));

        when(turGroupRepository.findById("1")).thenReturn(Optional.of(group));
        when(turUserRepository.findByTurGroupsIn(anyList()))
                .thenReturn(new HashSet<>(Collections.singletonList(existingUser)));
        when(turUserRepository.findByUsername("newUser")).thenReturn(newUser);
        when(turUserRepository.saveAndFlush(any(TurUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/v2/group/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(group)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Admin Group"));

        verify(turGroupRepository, times(1)).save(any(TurGroup.class));
        verify(turGroupRepository, times(1)).findById("1");
        verify(turUserRepository, times(1)).findByTurGroupsIn(anyList());
        verify(turUserRepository, times(1)).findByUsername("newUser");
        verify(turUserRepository, times(2)).saveAndFlush(any(TurUser.class));
    }

    @Test
    void testTurGroupUpdate_NotFound() throws Exception {
        TurGroup group = new TurGroup();
        group.setId("1");

        when(turGroupRepository.findById("1")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v2/group/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(group)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(turGroupRepository, times(1)).save(any(TurGroup.class));
        verify(turGroupRepository, times(1)).findById("1");
        verify(turUserRepository, never()).findByTurGroupsIn(anyList());
    }

    @Test
    void testTurGroupDelete() throws Exception {
        mockMvc.perform(delete("/api/v2/group/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(turGroupRepository, times(1)).deleteById("1");
    }

    @Test
    void testTurGroupAdd() throws Exception {
        TurGroup group = new TurGroup();
        group.setName("New Group");

        when(turGroupRepository.save(any(TurGroup.class))).thenReturn(group);

        mockMvc.perform(post("/api/v2/group")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(group)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Group"));

        verify(turGroupRepository, times(1)).save(any(TurGroup.class));
    }

    @Test
    void testTurGroupStructure() throws Exception {
        mockMvc.perform(get("/api/v2/group/model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());
    }
}
