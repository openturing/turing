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

import com.viglet.turing.domain.se.TurSEInstanceDomain;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;

/** Unit tests for {@link TurSEInstanceRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurSEInstanceRepositoryAdapterTest {

    @Mock
    private TurSEInstanceRepository turSEInstanceRepository;

    private TurSEInstanceRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurSEInstanceRepositoryAdapter(turSEInstanceRepository,
                Mappers.getMapper(TurSEInstanceDomainMapper.class));
    }

    @Test
    void findByIdReturnsMappedDomainWithVendorIdProjected() {
        when(turSEInstanceRepository.findById("se-1"))
                .thenReturn(Optional.of(buildEntity("se-1", "Solr", "vendor-solr", 1)));

        Optional<TurSEInstanceDomain> result = adapter.findById("se-1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("se-1");
        assertThat(result.get().title()).isEqualTo("Solr");
        assertThat(result.get().vendorId()).isEqualTo("vendor-solr");
        assertThat(result.get().isEnabled()).isTrue();
    }

    @Test
    void findByIdEmptyDelegatesEmpty() {
        when(turSEInstanceRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(adapter.findById("missing")).isEmpty();
    }

    @Test
    void findAllReturnsMappedDomainList() {
        when(turSEInstanceRepository.findAll())
                .thenReturn(List.of(buildEntity("a", "A", "v", 1), buildEntity("b", "B", "v", 0)));

        assertThat(adapter.findAll()).extracting(TurSEInstanceDomain::id).containsExactly("a", "b");
    }

    @Test
    void findAllEnabledFiltersByEnabledFlag() {
        when(turSEInstanceRepository.findAll())
                .thenReturn(List.of(buildEntity("a", "A", "v", 1), buildEntity("b", "B", "v", 0),
                        buildEntity("c", "C", "v", 1)));

        assertThat(adapter.findAllEnabled()).extracting(TurSEInstanceDomain::id)
                .containsExactly("a", "c");
    }

    private static TurSEInstance buildEntity(String id, String title, String vendorId,
            int enabled) {
        TurSEInstance entity = new TurSEInstance();
        entity.setId(id);
        entity.setTitle(title);
        entity.setEnabled(enabled);
        entity.setEndpointUrl("https://example.com");
        TurSEVendor vendor = new TurSEVendor();
        vendor.setId(vendorId);
        entity.setTurSEVendor(vendor);
        return entity;
    }
}
