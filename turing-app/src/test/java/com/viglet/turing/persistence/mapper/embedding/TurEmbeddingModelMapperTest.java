package com.viglet.turing.persistence.mapper.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.dto.embedding.TurEmbeddingModelDto;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;

/**
 * Tests for TurEmbeddingModelMapperImpl.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurEmbeddingModelMapperTest {

    private TurEmbeddingModelMapperImpl mapper;

    @BeforeEach
    void setUp() {
        mapper = new TurEmbeddingModelMapperImpl();
    }

    private TurEmbeddingModel buildEntity() {
        TurEmbeddingModel entity = new TurEmbeddingModel();
        entity.setId("emb-1");
        entity.setModelName("text-embedding-ada-002");
        entity.setDescription("OpenAI embedding model");
        entity.setIcon("embed-icon.png");
        entity.setProviderType("openai");
        entity.setModelReference("text-embedding-ada-002");
        entity.setBatchSize(256);
        entity.setModelPath("/models/ada");
        entity.setTokenizerPath("/tokenizers/ada");
        entity.setEnabled(1);
        return entity;
    }

    private TurEmbeddingModelDto buildDto() {
        TurEmbeddingModelDto dto = new TurEmbeddingModelDto();
        dto.setId("emb-2");
        dto.setModelName("nomic-embed-text");
        dto.setDescription("Nomic embedding model");
        dto.setIcon("nomic-icon.png");
        dto.setProviderType("ollama");
        dto.setModelReference("nomic-embed-text:latest");
        dto.setBatchSize(128);
        dto.setModelPath("/models/nomic");
        dto.setTokenizerPath("/tokenizers/nomic");
        dto.setEnabled(0);
        return dto;
    }

    @Test
    void shouldMapEntityToDto() {
        TurEmbeddingModel entity = buildEntity();

        TurEmbeddingModelDto dto = mapper.toDto(entity);

        assertNotNull(dto);
        assertEquals("emb-1", dto.getId());
        assertEquals("text-embedding-ada-002", dto.getModelName());
        assertEquals("OpenAI embedding model", dto.getDescription());
        assertEquals("embed-icon.png", dto.getIcon());
        assertEquals("openai", dto.getProviderType());
        assertEquals("text-embedding-ada-002", dto.getModelReference());
        assertEquals(256, dto.getBatchSize());
        assertEquals("/models/ada", dto.getModelPath());
        assertEquals("/tokenizers/ada", dto.getTokenizerPath());
        assertEquals(1, dto.getEnabled());
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertNull(mapper.toDto(null));
    }

    @Test
    void shouldMapDtoToEntity() {
        TurEmbeddingModelDto dto = buildDto();

        TurEmbeddingModel entity = mapper.toEntity(dto);

        assertNotNull(entity);
        assertEquals("emb-2", entity.getId());
        assertEquals("nomic-embed-text", entity.getModelName());
        assertEquals("Nomic embedding model", entity.getDescription());
        assertEquals("ollama", entity.getProviderType());
        assertEquals(128, entity.getBatchSize());
    }

    @Test
    void shouldReturnNullWhenDtoIsNull() {
        assertNull(mapper.toEntity(null));
    }

    @Test
    void shouldMapEntityListToDtoList() {
        List<TurEmbeddingModel> entities = List.of(buildEntity());

        List<TurEmbeddingModelDto> dtos = mapper.toDtoList(entities);

        assertNotNull(dtos);
        assertEquals(1, dtos.size());
        assertEquals("emb-1", dtos.get(0).getId());
    }

    @Test
    void shouldReturnNullForNullList() {
        assertNull(mapper.toDtoList(null));
    }

    @Test
    void shouldUpdateEntityIgnoringId() {
        TurEmbeddingModel source = buildEntity();
        TurEmbeddingModel target = new TurEmbeddingModel();
        target.setId("original-id");

        mapper.updateEntity(source, target);

        assertEquals("original-id", target.getId());
        assertEquals("text-embedding-ada-002", target.getModelName());
        assertEquals("OpenAI embedding model", target.getDescription());
        assertEquals("openai", target.getProviderType());
        assertEquals(256, target.getBatchSize());
    }

    @Test
    void shouldNotThrowWhenUpdateEntityWithNullSource() {
        TurEmbeddingModel target = new TurEmbeddingModel();
        target.setId("keep");
        target.setModelName("keep-model");

        mapper.updateEntity(null, target);

        assertEquals("keep", target.getId());
        assertEquals("keep-model", target.getModelName());
    }

    @Test
    void shouldHandleEntityWithNullFields() {
        TurEmbeddingModel entity = new TurEmbeddingModel();
        entity.setId("null-fields");

        TurEmbeddingModelDto dto = mapper.toDto(entity);

        assertNotNull(dto);
        assertEquals("null-fields", dto.getId());
        assertNull(dto.getModelName());
        assertNull(dto.getDescription());
    }
}
