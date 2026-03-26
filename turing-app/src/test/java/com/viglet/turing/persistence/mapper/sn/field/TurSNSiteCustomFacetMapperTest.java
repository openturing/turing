package com.viglet.turing.persistence.mapper.sn.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.dto.sn.field.TurSNSiteCustomFacetDto;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;

/**
 * Tests for TurSNSiteCustomFacetMapperImpl.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurSNSiteCustomFacetMapperTest {

    private TurSNSiteCustomFacetMapperImpl mapper;

    @BeforeEach
    void setUp() {
        mapper = new TurSNSiteCustomFacetMapperImpl();
    }

    private TurSNSiteCustomFacet buildEntity() {
        return TurSNSiteCustomFacet.builder()
                .id("cf-1")
                .name("Category")
                .defaultLabel("Categories")
                .facetPosition(1)
                .facetType(TurSNSiteFacetFieldEnum.DEFAULT)
                .facetItemType(TurSNSiteFacetFieldEnum.DEFAULT)
                .label(Map.of("en", "Categories", "pt", "Categorias"))
                .build();
    }

    private TurSNSiteCustomFacetDto buildDto() {
        TurSNSiteCustomFacetDto dto = new TurSNSiteCustomFacetDto();
        dto.setId("cf-2");
        dto.setName("Author");
        dto.setDefaultLabel("Authors");
        dto.setFacetPosition(2);
        dto.setFacetType(TurSNSiteFacetFieldEnum.DEFAULT);
        dto.setFacetItemType(TurSNSiteFacetFieldEnum.DEFAULT);
        return dto;
    }

    @Test
    void shouldMapEntityToDto() {
        TurSNSiteCustomFacet entity = buildEntity();

        TurSNSiteCustomFacetDto dto = mapper.toDto(entity);

        assertNotNull(dto);
        assertEquals("cf-1", dto.getId());
        assertEquals("Category", dto.getName());
        assertEquals("Categories", dto.getDefaultLabel());
        assertEquals(1, dto.getFacetPosition());
        assertEquals(TurSNSiteFacetFieldEnum.DEFAULT, dto.getFacetType());
        assertEquals(TurSNSiteFacetFieldEnum.DEFAULT, dto.getFacetItemType());
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertNull(mapper.toDto(null));
    }

    @Test
    void shouldMapDtoToEntity() {
        TurSNSiteCustomFacetDto dto = buildDto();

        TurSNSiteCustomFacet entity = mapper.toEntity(dto);

        assertNotNull(entity);
        assertEquals("cf-2", entity.getId());
        assertEquals("Author", entity.getName());
        assertEquals("Authors", entity.getDefaultLabel());
        assertEquals(2, entity.getFacetPosition());
    }

    @Test
    void shouldReturnNullWhenDtoIsNull() {
        assertNull(mapper.toEntity(null));
    }

    @Test
    void shouldMapEntityListToDtoList() {
        List<TurSNSiteCustomFacet> entities = List.of(buildEntity());

        List<TurSNSiteCustomFacetDto> dtos = mapper.toDtoList(entities);

        assertNotNull(dtos);
        assertEquals(1, dtos.size());
        assertEquals("cf-1", dtos.get(0).getId());
    }

    @Test
    void shouldReturnNullForNullList() {
        assertNull(mapper.toDtoList(null));
    }

    @Test
    void shouldMapEntitySetToDtoSet() {
        Set<TurSNSiteCustomFacet> entities = Set.of(buildEntity());

        Set<TurSNSiteCustomFacetDto> dtos = mapper.toDtoSet(entities);

        assertNotNull(dtos);
        assertEquals(1, dtos.size());
    }

    @Test
    void shouldReturnNullForNullSet() {
        assertNull(mapper.toDtoSet(null));
    }

    @Test
    void shouldHandleEntityWithNullFields() {
        TurSNSiteCustomFacet entity = TurSNSiteCustomFacet.builder()
                .id("null-cf")
                .build();

        TurSNSiteCustomFacetDto dto = mapper.toDto(entity);

        assertNotNull(dto);
        assertEquals("null-cf", dto.getId());
        assertNull(dto.getName());
        assertNull(dto.getDefaultLabel());
    }
}
