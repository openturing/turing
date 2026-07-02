/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.customtool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.customtool.TurCustomToolDomain;
import com.viglet.turing.persistence.model.customtool.TurCustomTool;
import com.viglet.turing.persistence.repository.customtool.TurCustomToolRepository;

/** Unit tests for {@link TurCustomToolReader} (T546 — port collapsed to a mapper-backed reader). */
@ExtendWith(MockitoExtension.class)
class TurCustomToolReaderTest {

    @Mock
    private TurCustomToolRepository turCustomToolRepository;

    private TurCustomToolReader reader;

    @BeforeEach
    void setUp() {
        reader = new TurCustomToolReader(turCustomToolRepository,
                Mappers.getMapper(TurCustomToolDomainMapper.class));
    }

    @Test
    void findByIdReturnsMappedDomainWithGroovyScript() {
        TurCustomTool entity = buildEntity("tool-1", "Calculator", 1);
        entity.setGroovyScript("return params.a + params.b");
        entity.setParametersJson("[{\"name\":\"a\",\"type\":\"number\"}]");
        entity.setReturnType("number");
        when(turCustomToolRepository.findById("tool-1")).thenReturn(Optional.of(entity));

        Optional<TurCustomToolDomain> result = reader.findById("tool-1");

        assertThat(result).isPresent();
        assertThat(result.get().groovyScript()).isEqualTo("return params.a + params.b");
        assertThat(result.get().parametersJson()).contains("\"name\":\"a\"");
        assertThat(result.get().returnType()).isEqualTo("number");
        assertThat(result.get().isEnabled()).isTrue();
    }

    @Test
    void findByIdEmpty() {
        when(turCustomToolRepository.findById("missing")).thenReturn(Optional.empty());
        assertThat(reader.findById("missing")).isEmpty();
    }

    @Test
    void findAllEnabledFiltersByEnabledFlag() {
        when(turCustomToolRepository.findAll()).thenReturn(List.of(buildEntity("a", "A", 1),
                buildEntity("b", "B", 0), buildEntity("c", "C", 1)));

        assertThat(reader.findAllEnabled()).extracting(TurCustomToolDomain::id)
                .containsExactly("a", "c");
    }

    private static TurCustomTool buildEntity(String id, String title, int enabled) {
        TurCustomTool entity = new TurCustomTool();
        entity.setId(id);
        entity.setTitle(title);
        entity.setLlmDescription("desc");
        entity.setReturnType("string");
        entity.setEnabled(enabled);
        return entity;
    }
}
