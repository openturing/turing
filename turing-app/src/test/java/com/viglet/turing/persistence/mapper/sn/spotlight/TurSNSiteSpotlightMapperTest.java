package com.viglet.turing.persistence.mapper.sn.spotlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.dto.sn.spotlight.TurSNSiteSpotlightDto;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;

/**
 * Tests for TurSNSiteSpotlightMapperImpl.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurSNSiteSpotlightMapperTest {

    private TurSNSiteSpotlightMapperImpl mapper;

    @BeforeEach
    void setUp() {
        mapper = new TurSNSiteSpotlightMapperImpl();
    }

    private TurSNSiteSpotlight buildEntity() {
        TurSNSiteSpotlight entity = new TurSNSiteSpotlight();
        entity.setId("spot-1");
        entity.setName("Featured");
        entity.setDescription("Featured spotlight");
        entity.setManaged(1);
        entity.setUnmanagedId("ext-123");
        entity.setProvider("TURING");
        entity.setLanguage(Locale.ENGLISH);
        entity.setModificationDate(LocalDateTime.of(2026, 3, 26, 10, 0));
        return entity;
    }

    private TurSNSiteSpotlightDto buildDto() {
        TurSNSiteSpotlightDto dto = new TurSNSiteSpotlightDto();
        dto.setId("spot-2");
        dto.setName("Promo");
        dto.setDescription("Promotional spotlight");
        dto.setManaged(0);
        dto.setUnmanagedId("ext-456");
        dto.setProvider("EXTERNAL");
        dto.setLanguage(Locale.FRENCH);
        dto.setModificationDate(LocalDateTime.of(2026, 1, 15, 12, 30));
        return dto;
    }

    @Test
    void shouldMapEntityToDto() {
        TurSNSiteSpotlight entity = buildEntity();

        TurSNSiteSpotlightDto dto = mapper.toDto(entity);

        assertNotNull(dto);
        assertEquals("spot-1", dto.getId());
        assertEquals("Featured", dto.getName());
        assertEquals("Featured spotlight", dto.getDescription());
        assertEquals(1, dto.getManaged());
        assertEquals("ext-123", dto.getUnmanagedId());
        assertEquals("TURING", dto.getProvider());
        assertEquals(Locale.ENGLISH, dto.getLanguage());
        assertEquals(LocalDateTime.of(2026, 3, 26, 10, 0), dto.getModificationDate());
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertNull(mapper.toDto(null));
    }

    @Test
    void shouldMapDtoToEntity() {
        TurSNSiteSpotlightDto dto = buildDto();

        TurSNSiteSpotlight entity = mapper.toEntity(dto);

        assertNotNull(entity);
        assertEquals("spot-2", entity.getId());
        assertEquals("Promo", entity.getName());
        assertEquals("Promotional spotlight", entity.getDescription());
        assertEquals(0, entity.getManaged());
        assertEquals("ext-456", entity.getUnmanagedId());
        assertEquals("EXTERNAL", entity.getProvider());
        assertEquals(Locale.FRENCH, entity.getLanguage());
    }

    @Test
    void shouldReturnNullWhenDtoIsNull() {
        assertNull(mapper.toEntity(null));
    }

    @Test
    void shouldMapEntityListToDtoList() {
        List<TurSNSiteSpotlight> entities = List.of(buildEntity());

        List<TurSNSiteSpotlightDto> dtos = mapper.toDtoList(entities);

        assertNotNull(dtos);
        assertEquals(1, dtos.size());
        assertEquals("spot-1", dtos.get(0).getId());
    }

    @Test
    void shouldReturnNullForNullList() {
        assertNull(mapper.toDtoList(null));
    }

    @Test
    void shouldMapEntitySetToDtoSet() {
        Set<TurSNSiteSpotlight> entities = Set.of(buildEntity());

        Set<TurSNSiteSpotlightDto> dtos = mapper.toDtoSet(entities);

        assertNotNull(dtos);
        assertEquals(1, dtos.size());
    }

    @Test
    void shouldReturnNullForNullSet() {
        assertNull(mapper.toDtoSet(null));
    }

    @Test
    void shouldHandleEntityWithNullFields() {
        TurSNSiteSpotlight entity = new TurSNSiteSpotlight();
        entity.setId("null-spot");

        TurSNSiteSpotlightDto dto = mapper.toDto(entity);

        assertNotNull(dto);
        assertEquals("null-spot", dto.getId());
        assertNull(dto.getName());
        assertNull(dto.getDescription());
        assertNull(dto.getLanguage());
    }
}
