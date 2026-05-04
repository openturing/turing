/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.se;

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

import com.viglet.turing.domain.se.TurSEVendorDomain;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;

/** Unit tests for {@link TurSEVendorRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurSEVendorRepositoryAdapterTest {

    @Mock
    private TurSEVendorRepository turSEVendorRepository;

    private TurSEVendorRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurSEVendorRepositoryAdapter(turSEVendorRepository,
                Mappers.getMapper(TurSEVendorDomainMapper.class));
    }

    @Test
    void findByIdReturnsMappedDomain() {
        when(turSEVendorRepository.findById("solr")).thenReturn(Optional.of(buildEntity("solr",
                "Apache Solr", "solr-plugin")));

        Optional<TurSEVendorDomain> result = adapter.findById("solr");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("solr");
        assertThat(result.get().title()).isEqualTo("Apache Solr");
        assertThat(result.get().plugin()).isEqualTo("solr-plugin");
    }

    @Test
    void findByIdEmpty() {
        when(turSEVendorRepository.findById("missing")).thenReturn(Optional.empty());
        assertThat(adapter.findById("missing")).isEmpty();
    }

    @Test
    void findAllReturnsMappedDomainList() {
        when(turSEVendorRepository.findAll()).thenReturn(List.of(buildEntity("a", "A", "p"),
                buildEntity("b", "B", "p")));
        assertThat(adapter.findAll()).extracting(TurSEVendorDomain::id).containsExactly("a", "b");
    }

    private static TurSEVendor buildEntity(String id, String title, String plugin) {
        TurSEVendor entity = new TurSEVendor();
        entity.setId(id);
        entity.setTitle(title);
        entity.setPlugin(plugin);
        return entity;
    }
}
