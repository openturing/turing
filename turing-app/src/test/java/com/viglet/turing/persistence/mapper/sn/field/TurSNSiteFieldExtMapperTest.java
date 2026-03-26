package com.viglet.turing.persistence.mapper.sn.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.sn.TurSNFieldType;

/**
 * Tests for TurSNSiteFieldExtMapperImpl.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteFieldExtMapperTest {

    @Spy
    private TurSNSiteFieldExtFacetMapperImpl turSNSiteFieldExtFacetMapper;

    @InjectMocks
    private TurSNSiteFieldExtMapperImpl mapper;

    private TurSNSiteFieldExt buildEntity() {
        return TurSNSiteFieldExt.builder()
                .id("fe-1")
                .externalId("ext-1")
                .name("title")
                .description("Title field")
                .facetName("Title")
                .snType(TurSNFieldType.NER)
                .type(TurSEFieldType.TEXT)
                .multiValued(0)
                .facet(1)
                .hl(1)
                .mlt(0)
                .enabled(1)
                .required(1)
                .defaultValue("Untitled")
                .build();
    }

    private TurSNSiteFieldExtDto buildDto() {
        return TurSNSiteFieldExtDto.builder()
                .id("fe-2")
                .externalId("ext-2")
                .name("author")
                .description("Author field")
                .facetName("Author")
                .snType(TurSNFieldType.NER)
                .type(TurSEFieldType.STRING)
                .multiValued(1)
                .facet(1)
                .hl(0)
                .mlt(1)
                .enabled(1)
                .required(0)
                .defaultValue("Unknown")
                .build();
    }

    @Test
    void shouldMapEntityToDto() {
        TurSNSiteFieldExt entity = buildEntity();

        TurSNSiteFieldExtDto dto = mapper.toDto(entity);

        assertNotNull(dto);
        assertEquals("fe-1", dto.getId());
        assertEquals("ext-1", dto.getExternalId());
        assertEquals("title", dto.getName());
        assertEquals("Title field", dto.getDescription());
        assertEquals("Title", dto.getFacetName());
        assertEquals(TurSNFieldType.NER, dto.getSnType());
        assertEquals(TurSEFieldType.TEXT, dto.getType());
        assertEquals(0, dto.getMultiValued());
        assertEquals(1, dto.getFacet());
        assertEquals(1, dto.getHl());
        assertEquals(0, dto.getMlt());
        assertEquals(1, dto.getEnabled());
        assertEquals(1, dto.getRequired());
        assertEquals("Untitled", dto.getDefaultValue());
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertNull(mapper.toDto(null));
    }

    @Test
    void shouldMapDtoToEntity() {
        TurSNSiteFieldExtDto dto = buildDto();

        TurSNSiteFieldExt entity = mapper.toEntity(dto);

        assertNotNull(entity);
        assertEquals("fe-2", entity.getId());
        assertEquals("ext-2", entity.getExternalId());
        assertEquals("author", entity.getName());
        assertEquals("Author field", entity.getDescription());
        assertEquals("Author", entity.getFacetName());
        assertEquals(TurSNFieldType.NER, entity.getSnType());
        assertEquals(TurSEFieldType.STRING, entity.getType());
        assertEquals(1, entity.getMultiValued());
        assertEquals(1, entity.getFacet());
        assertEquals(0, entity.getHl());
        assertEquals(1, entity.getMlt());
        assertEquals(1, entity.getEnabled());
        assertEquals(0, entity.getRequired());
        assertEquals("Unknown", entity.getDefaultValue());
    }

    @Test
    void shouldReturnNullWhenDtoIsNull() {
        assertNull(mapper.toEntity(null));
    }

    @Test
    void shouldMapEntityListToDtoList() {
        List<TurSNSiteFieldExt> entities = List.of(buildEntity());

        List<TurSNSiteFieldExtDto> dtos = mapper.toDtoList(entities);

        assertNotNull(dtos);
        assertEquals(1, dtos.size());
        assertEquals("fe-1", dtos.get(0).getId());
    }

    @Test
    void shouldReturnNullForNullList() {
        assertNull(mapper.toDtoList(null));
    }

    @Test
    void shouldMapEntitySetToDtoSet() {
        Set<TurSNSiteFieldExt> entities = Set.of(buildEntity());

        Set<TurSNSiteFieldExtDto> dtos = mapper.toDtoSet(entities);

        assertNotNull(dtos);
        assertEquals(1, dtos.size());
    }

    @Test
    void shouldReturnNullForNullSet() {
        assertNull(mapper.toDtoSet(null));
    }

    @Test
    void shouldHandleEntityWithNullFields() {
        TurSNSiteFieldExt entity = new TurSNSiteFieldExt();
        entity.setId("null-fe");

        TurSNSiteFieldExtDto dto = mapper.toDto(entity);

        assertNotNull(dto);
        assertEquals("null-fe", dto.getId());
        assertNull(dto.getName());
        assertNull(dto.getDescription());
        assertNull(dto.getType());
    }
}
