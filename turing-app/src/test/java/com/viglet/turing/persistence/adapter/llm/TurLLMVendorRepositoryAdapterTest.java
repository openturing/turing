/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.llm;

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

import com.viglet.turing.domain.llm.TurLLMVendorDomain;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;

/** Unit tests for {@link TurLLMVendorRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurLLMVendorRepositoryAdapterTest {

    @Mock
    private TurLLMVendorRepository turLLMVendorRepository;

    private TurLLMVendorRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurLLMVendorRepositoryAdapter(turLLMVendorRepository,
                Mappers.getMapper(TurLLMVendorDomainMapper.class));
    }

    @Test
    void findByIdReturnsMappedDomain() {
        when(turLLMVendorRepository.findById("openai"))
                .thenReturn(Optional.of(buildEntity("openai", "OpenAI", "openai-plugin")));

        Optional<TurLLMVendorDomain> result = adapter.findById("openai");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("openai");
        assertThat(result.get().title()).isEqualTo("OpenAI");
        assertThat(result.get().plugin()).isEqualTo("openai-plugin");
    }

    @Test
    void findByIdEmpty() {
        when(turLLMVendorRepository.findById("missing")).thenReturn(Optional.empty());
        assertThat(adapter.findById("missing")).isEmpty();
    }

    @Test
    void findAllReturnsMappedDomainList() {
        when(turLLMVendorRepository.findAll()).thenReturn(List.of(buildEntity("a", "A", "p"),
                buildEntity("b", "B", "p")));
        assertThat(adapter.findAll()).extracting(TurLLMVendorDomain::id).containsExactly("a", "b");
    }

    private static TurLLMVendor buildEntity(String id, String title, String plugin) {
        TurLLMVendor entity = new TurLLMVendor();
        entity.setId(id);
        entity.setTitle(title);
        entity.setPlugin(plugin);
        return entity;
    }
}
