package com.viglet.turing.persistence.mapper.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.dto.mcp.TurMcpServerDto;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.mcp.TurMcpServerConnectionType;
import com.viglet.turing.persistence.model.mcp.TurMcpServerType;

/**
 * Tests for TurMcpServerMapperImpl.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurMcpServerMapperTest {

    private TurMcpServerMapperImpl mapper;

    @BeforeEach
    void setUp() {
        mapper = new TurMcpServerMapperImpl();
    }

    private TurMcpServer buildEntity() {
        TurMcpServer entity = new TurMcpServer();
        entity.setId("mcp-1");
        entity.setTitle("Test MCP");
        entity.setDescription("A test MCP server");
        entity.setIcon("icon.png");
        entity.setUrl("http://localhost:3000");
        entity.setCommand("node");
        entity.setArgs("--port 3000");
        entity.setType(TurMcpServerType.SYNC);
        entity.setConnectionType(TurMcpServerConnectionType.HTTP);
        entity.setEnabled(1);
        return entity;
    }

    private TurMcpServerDto buildDto() {
        TurMcpServerDto dto = new TurMcpServerDto();
        dto.setId("mcp-2");
        dto.setTitle("DTO MCP");
        dto.setDescription("A DTO MCP server");
        dto.setIcon("dto-icon.png");
        dto.setUrl("http://localhost:4000");
        dto.setCommand("python");
        dto.setArgs("-m server");
        dto.setType(TurMcpServerType.ASYNC);
        dto.setConnectionType(TurMcpServerConnectionType.COMMAND);
        dto.setEnabled(0);
        return dto;
    }

    @Test
    void shouldMapEntityToDto() {
        TurMcpServer entity = buildEntity();

        TurMcpServerDto dto = mapper.toDto(entity);

        assertNotNull(dto);
        assertEquals("mcp-1", dto.getId());
        assertEquals("Test MCP", dto.getTitle());
        assertEquals("A test MCP server", dto.getDescription());
        assertEquals("icon.png", dto.getIcon());
        assertEquals("http://localhost:3000", dto.getUrl());
        assertEquals("node", dto.getCommand());
        assertEquals("--port 3000", dto.getArgs());
        assertEquals(TurMcpServerType.SYNC, dto.getType());
        assertEquals(TurMcpServerConnectionType.HTTP, dto.getConnectionType());
        assertEquals(1, dto.getEnabled());
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertNull(mapper.toDto(null));
    }

    @Test
    void shouldMapDtoToEntity() {
        TurMcpServerDto dto = buildDto();

        TurMcpServer entity = mapper.toEntity(dto);

        assertNotNull(entity);
        assertEquals("mcp-2", entity.getId());
        assertEquals("DTO MCP", entity.getTitle());
        assertEquals("A DTO MCP server", entity.getDescription());
        assertEquals("dto-icon.png", entity.getIcon());
        assertEquals("http://localhost:4000", entity.getUrl());
        assertEquals("python", entity.getCommand());
        assertEquals("-m server", entity.getArgs());
        assertEquals(TurMcpServerType.ASYNC, entity.getType());
        assertEquals(TurMcpServerConnectionType.COMMAND, entity.getConnectionType());
        assertEquals(0, entity.getEnabled());
    }

    @Test
    void shouldReturnNullWhenDtoIsNull() {
        assertNull(mapper.toEntity(null));
    }

    @Test
    void shouldMapEntityListToDtoList() {
        List<TurMcpServer> entities = List.of(buildEntity());

        List<TurMcpServerDto> dtos = mapper.toDtoList(entities);

        assertNotNull(dtos);
        assertEquals(1, dtos.size());
        assertEquals("mcp-1", dtos.get(0).getId());
    }

    @Test
    void shouldReturnNullForNullList() {
        assertNull(mapper.toDtoList(null));
    }

    @Test
    void shouldMapEntitySetToDtoSet() {
        Set<TurMcpServer> entities = Set.of(buildEntity());

        Set<TurMcpServerDto> dtos = mapper.toDtoSet(entities);

        assertNotNull(dtos);
        assertEquals(1, dtos.size());
    }

    @Test
    void shouldReturnNullForNullSet() {
        assertNull(mapper.toDtoSet(null));
    }

    @Test
    void shouldUpdateEntityIgnoringId() {
        TurMcpServer source = buildEntity();
        TurMcpServer target = new TurMcpServer();
        target.setId("original-id");

        mapper.updateEntity(source, target);

        assertEquals("original-id", target.getId());
        assertEquals("Test MCP", target.getTitle());
        assertEquals("A test MCP server", target.getDescription());
        assertEquals("icon.png", target.getIcon());
        assertEquals("http://localhost:3000", target.getUrl());
        assertEquals("node", target.getCommand());
        assertEquals("--port 3000", target.getArgs());
        assertEquals(TurMcpServerType.SYNC, target.getType());
        assertEquals(TurMcpServerConnectionType.HTTP, target.getConnectionType());
        assertEquals(1, target.getEnabled());
    }

    @Test
    void shouldNotThrowWhenUpdateEntityWithNullSource() {
        TurMcpServer target = new TurMcpServer();
        target.setId("keep");
        target.setTitle("keep-title");

        mapper.updateEntity(null, target);

        assertEquals("keep", target.getId());
        assertEquals("keep-title", target.getTitle());
    }

    @Test
    void shouldHandleEntityWithNullFields() {
        TurMcpServer entity = new TurMcpServer();
        entity.setId("null-fields");
        entity.setType(TurMcpServerType.SYNC);
        entity.setConnectionType(TurMcpServerConnectionType.HTTP);

        TurMcpServerDto dto = mapper.toDto(entity);

        assertNotNull(dto);
        assertEquals("null-fields", dto.getId());
        assertNull(dto.getTitle());
        assertNull(dto.getDescription());
        assertNull(dto.getUrl());
    }
}
