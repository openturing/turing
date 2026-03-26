package com.viglet.turing.persistence.mapper.intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.dto.intent.TurIntentDto;
import com.viglet.turing.persistence.model.intent.TurIntent;

/**
 * Tests for TurIntentMapperImpl.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurIntentMapperTest {

    private TurIntentMapperImpl mapper;

    @BeforeEach
    void setUp() {
        mapper = new TurIntentMapperImpl();
    }

    private TurIntent buildEntity() {
        TurIntent entity = new TurIntent();
        entity.setId("intent-1");
        entity.setTitle("Search Intent");
        entity.setDescription("A search intent");
        entity.setIcon("search-icon.png");
        entity.setEnabled(1);
        entity.setSortOrder(10);
        return entity;
    }

    private TurIntentDto buildDto() {
        TurIntentDto dto = new TurIntentDto();
        dto.setId("intent-2");
        dto.setTitle("Chat Intent");
        dto.setDescription("A chat intent");
        dto.setIcon("chat-icon.png");
        dto.setEnabled(0);
        dto.setSortOrder(5);
        return dto;
    }

    @Test
    void shouldMapEntityToDto() {
        TurIntent entity = buildEntity();

        TurIntentDto dto = mapper.toDto(entity);

        assertNotNull(dto);
        assertEquals("intent-1", dto.getId());
        assertEquals("Search Intent", dto.getTitle());
        assertEquals("A search intent", dto.getDescription());
        assertEquals("search-icon.png", dto.getIcon());
        assertEquals(1, dto.getEnabled());
        assertEquals(10, dto.getSortOrder());
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertNull(mapper.toDto(null));
    }

    @Test
    void shouldMapDtoToEntity() {
        TurIntentDto dto = buildDto();

        TurIntent entity = mapper.toEntity(dto);

        assertNotNull(entity);
        assertEquals("intent-2", entity.getId());
        assertEquals("Chat Intent", entity.getTitle());
        assertEquals("A chat intent", entity.getDescription());
        assertEquals("chat-icon.png", entity.getIcon());
        assertEquals(0, entity.getEnabled());
        assertEquals(5, entity.getSortOrder());
    }

    @Test
    void shouldReturnNullWhenDtoIsNull() {
        assertNull(mapper.toEntity(null));
    }

    @Test
    void shouldMapEntityListToDtoList() {
        List<TurIntent> entities = List.of(buildEntity());

        List<TurIntentDto> dtos = mapper.toDtoList(entities);

        assertNotNull(dtos);
        assertEquals(1, dtos.size());
        assertEquals("intent-1", dtos.get(0).getId());
    }

    @Test
    void shouldReturnNullForNullList() {
        assertNull(mapper.toDtoList(null));
    }

    @Test
    void shouldUpdateEntityIgnoringId() {
        TurIntent source = buildEntity();
        TurIntent target = new TurIntent();
        target.setId("original-id");

        mapper.updateEntity(source, target);

        assertEquals("original-id", target.getId());
        assertEquals("Search Intent", target.getTitle());
        assertEquals("A search intent", target.getDescription());
        assertEquals("search-icon.png", target.getIcon());
        assertEquals(1, target.getEnabled());
        assertEquals(10, target.getSortOrder());
    }

    @Test
    void shouldNotThrowWhenUpdateEntityWithNullSource() {
        TurIntent target = new TurIntent();
        target.setId("keep");
        target.setTitle("keep-title");

        mapper.updateEntity(null, target);

        assertEquals("keep", target.getId());
        assertEquals("keep-title", target.getTitle());
    }

    @Test
    void shouldHandleEntityWithNullFields() {
        TurIntent entity = new TurIntent();
        entity.setId("null-intent");

        TurIntentDto dto = mapper.toDto(entity);

        assertNotNull(dto);
        assertEquals("null-intent", dto.getId());
        assertNull(dto.getTitle());
        assertNull(dto.getDescription());
    }
}
