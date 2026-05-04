/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.store;

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

import com.viglet.turing.domain.store.TurStoreVendorDomain;
import com.viglet.turing.persistence.model.store.TurStoreVendor;
import com.viglet.turing.persistence.repository.store.TurStoreVendorRepository;

/** Unit tests for {@link TurStoreVendorRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurStoreVendorRepositoryAdapterTest {

    @Mock
    private TurStoreVendorRepository turStoreVendorRepository;

    private TurStoreVendorRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurStoreVendorRepositoryAdapter(turStoreVendorRepository,
                Mappers.getMapper(TurStoreVendorDomainMapper.class));
    }

    @Test
    void findByIdReturnsMappedDomain() {
        when(turStoreVendorRepository.findById("qdrant"))
                .thenReturn(Optional.of(buildEntity("qdrant", "Qdrant", "qdrant-plugin")));

        Optional<TurStoreVendorDomain> result = adapter.findById("qdrant");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("qdrant");
        assertThat(result.get().title()).isEqualTo("Qdrant");
        assertThat(result.get().plugin()).isEqualTo("qdrant-plugin");
    }

    @Test
    void findByIdEmpty() {
        when(turStoreVendorRepository.findById("missing")).thenReturn(Optional.empty());
        assertThat(adapter.findById("missing")).isEmpty();
    }

    @Test
    void findAllReturnsMappedDomainList() {
        when(turStoreVendorRepository.findAll()).thenReturn(List.of(buildEntity("a", "A", "p"),
                buildEntity("b", "B", "p")));
        assertThat(adapter.findAll()).extracting(TurStoreVendorDomain::id).containsExactly("a",
                "b");
    }

    private static TurStoreVendor buildEntity(String id, String title, String plugin) {
        TurStoreVendor entity = new TurStoreVendor();
        entity.setId(id);
        entity.setTitle(title);
        entity.setPlugin(plugin);
        return entity;
    }
}
