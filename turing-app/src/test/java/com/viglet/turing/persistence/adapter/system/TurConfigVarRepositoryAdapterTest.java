/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.system;

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

import com.viglet.turing.domain.system.TurConfigVarDomain;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

/** Unit tests for {@link TurConfigVarRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurConfigVarRepositoryAdapterTest {

    @Mock
    private TurConfigVarRepository turConfigVarRepository;

    private TurConfigVarRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurConfigVarRepositoryAdapter(turConfigVarRepository,
                Mappers.getMapper(TurConfigVarDomainMapper.class));
    }

    @Test
    void findByIdReturnsMappedDomain() {
        TurConfigVar entity = new TurConfigVar();
        entity.setId("UI_THEME");
        entity.setPath("ui.theme");
        entity.setValue("dark");
        when(turConfigVarRepository.findById("UI_THEME")).thenReturn(Optional.of(entity));

        TurConfigVarDomain domain = adapter.findById("UI_THEME").orElseThrow();

        assertThat(domain.id()).isEqualTo("UI_THEME");
        assertThat(domain.path()).isEqualTo("ui.theme");
        assertThat(domain.value()).isEqualTo("dark");
    }

    @Test
    void findByIdEmpty() {
        when(turConfigVarRepository.findById("missing")).thenReturn(Optional.empty());
        assertThat(adapter.findById("missing")).isEmpty();
    }

    @Test
    void findAllReturnsMappedDomainList() {
        TurConfigVar a = new TurConfigVar();
        a.setId("A");
        TurConfigVar b = new TurConfigVar();
        b.setId("B");
        when(turConfigVarRepository.findAll()).thenReturn(List.of(a, b));

        assertThat(adapter.findAll()).extracting(TurConfigVarDomain::id).containsExactly("A", "B");
    }
}
