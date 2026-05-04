/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.mcp;

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

import com.viglet.turing.domain.mcp.TurMcpServerDomain;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.mcp.TurMcpServerConnectionType;
import com.viglet.turing.persistence.model.mcp.TurMcpServerType;
import com.viglet.turing.persistence.repository.mcp.TurMcpServerRepository;

/** Unit tests for {@link TurMcpServerRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurMcpServerRepositoryAdapterTest {

    @Mock
    private TurMcpServerRepository turMcpServerRepository;

    private TurMcpServerRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurMcpServerRepositoryAdapter(turMcpServerRepository,
                Mappers.getMapper(TurMcpServerDomainMapper.class));
    }

    @Test
    void findByIdReturnsMappedDomainPreservingEnumFields() {
        TurMcpServer entity = buildEntity("mcp-1", "Search MCP", 1);
        when(turMcpServerRepository.findById("mcp-1")).thenReturn(Optional.of(entity));

        Optional<TurMcpServerDomain> result = adapter.findById("mcp-1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("mcp-1");
        assertThat(result.get().title()).isEqualTo("Search MCP");
        assertThat(result.get().type()).isEqualTo(entity.getType());
        assertThat(result.get().connectionType()).isEqualTo(entity.getConnectionType());
        assertThat(result.get().isEnabled()).isTrue();
    }

    @Test
    void findByIdEmpty() {
        when(turMcpServerRepository.findById("missing")).thenReturn(Optional.empty());
        assertThat(adapter.findById("missing")).isEmpty();
    }

    @Test
    void findAllEnabledFiltersByEnabledFlag() {
        when(turMcpServerRepository.findAll()).thenReturn(List.of(buildEntity("a", "A", 1),
                buildEntity("b", "B", 0), buildEntity("c", "C", 1)));

        assertThat(adapter.findAllEnabled()).extracting(TurMcpServerDomain::id).containsExactly("a",
                "c");
    }

    private static TurMcpServer buildEntity(String id, String title, int enabled) {
        TurMcpServer entity = new TurMcpServer();
        entity.setId(id);
        entity.setTitle(title);
        entity.setEnabled(enabled);
        // Pick the first enum constants so the test works regardless of which
        // names exist; the assertion only checks that mapping preserves the value.
        TurMcpServerType[] types = TurMcpServerType.values();
        TurMcpServerConnectionType[] conns = TurMcpServerConnectionType.values();
        if (types.length > 0) {
            entity.setType(types[0]);
        }
        if (conns.length > 0) {
            entity.setConnectionType(conns[0]);
        }
        return entity;
    }
}
