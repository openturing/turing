package com.viglet.turing.persistence.mapper.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.dto.agent.TurAIAgentDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;

/**
 * Tests for TurAIAgentMapperImpl.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurAIAgentMapperTest {

    private TurAIAgentMapperImpl mapper;

    @BeforeEach
    void setUp() {
        mapper = new TurAIAgentMapperImpl();
    }

    private TurAIAgent buildEntity() {
        TurAIAgent entity = new TurAIAgent();
        entity.setId("agent-1");
        entity.setTitle("Search Agent");
        entity.setDescription("An AI search agent");
        entity.setIcon("agent-icon.png");
        entity.setSystemPrompt("You are a search assistant.");
        entity.setEnabled(1);
        entity.setNativeTools("search,analyze");
        return entity;
    }

    private TurAIAgentDto buildDto() {
        TurAIAgentDto dto = new TurAIAgentDto();
        dto.setId("agent-2");
        dto.setTitle("Chat Agent");
        dto.setDescription("A chat agent");
        dto.setIcon("chat-icon.png");
        dto.setSystemPrompt("You are a chat assistant.");
        dto.setEnabled(0);
        dto.setNativeTools("chat");
        return dto;
    }

    @Test
    void shouldMapEntityToDto() {
        TurAIAgent entity = buildEntity();

        TurAIAgentDto dto = mapper.toDto(entity);

        assertNotNull(dto);
        assertEquals("agent-1", dto.getId());
        assertEquals("Search Agent", dto.getTitle());
        assertEquals("An AI search agent", dto.getDescription());
        assertEquals("agent-icon.png", dto.getIcon());
        assertEquals("You are a search assistant.", dto.getSystemPrompt());
        assertEquals(1, dto.getEnabled());
        assertEquals("search,analyze", dto.getNativeTools());
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertNull(mapper.toDto(null));
    }

    @Test
    void shouldMapDtoToEntity() {
        TurAIAgentDto dto = buildDto();

        TurAIAgent entity = mapper.toEntity(dto);

        assertNotNull(entity);
        assertEquals("agent-2", entity.getId());
        assertEquals("Chat Agent", entity.getTitle());
        assertEquals("A chat agent", entity.getDescription());
        assertEquals("chat-icon.png", entity.getIcon());
        assertEquals("You are a chat assistant.", entity.getSystemPrompt());
        assertEquals(0, entity.getEnabled());
        assertEquals("chat", entity.getNativeTools());
    }

    @Test
    void shouldReturnNullWhenDtoIsNull() {
        assertNull(mapper.toEntity(null));
    }

    @Test
    void shouldMapEntityListToDtoList() {
        List<TurAIAgent> entities = List.of(buildEntity());

        List<TurAIAgentDto> dtos = mapper.toDtoList(entities);

        assertNotNull(dtos);
        assertEquals(1, dtos.size());
        assertEquals("agent-1", dtos.get(0).getId());
    }

    @Test
    void shouldReturnNullForNullList() {
        assertNull(mapper.toDtoList(null));
    }

    @Test
    void shouldMapEntitySetToDtoSet() {
        Set<TurAIAgent> entities = Set.of(buildEntity());

        Set<TurAIAgentDto> dtos = mapper.toDtoSet(entities);

        assertNotNull(dtos);
        assertEquals(1, dtos.size());
    }

    @Test
    void shouldReturnNullForNullSet() {
        assertNull(mapper.toDtoSet(null));
    }

    @Test
    void shouldHandleEntityWithNullFields() {
        TurAIAgent entity = new TurAIAgent();
        entity.setId("null-agent");

        TurAIAgentDto dto = mapper.toDto(entity);

        assertNotNull(dto);
        assertEquals("null-agent", dto.getId());
        assertNull(dto.getTitle());
        assertNull(dto.getDescription());
        assertNull(dto.getSystemPrompt());
    }
}
