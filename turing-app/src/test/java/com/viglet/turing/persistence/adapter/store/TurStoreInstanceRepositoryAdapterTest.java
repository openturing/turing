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

import com.viglet.turing.domain.store.TurStoreInstanceDomain;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.model.store.TurStoreVendor;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;

/**
 * Unit tests for {@link TurStoreInstanceRepositoryAdapter}, including the
 * codified invariant that the encrypted credential is never projected onto
 * the domain record.
 */
@ExtendWith(MockitoExtension.class)
class TurStoreInstanceRepositoryAdapterTest {

    @Mock
    private TurStoreInstanceRepository turStoreInstanceRepository;

    private TurStoreInstanceRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurStoreInstanceRepositoryAdapter(turStoreInstanceRepository,
                Mappers.getMapper(TurStoreInstanceDomainMapper.class));
    }

    @Test
    void findByIdReturnsMappedDomainWithVendorIdProjected() {
        TurStoreInstance entity = buildEntity("store-1", "Qdrant", "qdrant", 1);
        when(turStoreInstanceRepository.findById("store-1")).thenReturn(Optional.of(entity));

        Optional<TurStoreInstanceDomain> result = adapter.findById("store-1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("store-1");
        assertThat(result.get().title()).isEqualTo("Qdrant");
        assertThat(result.get().vendorId()).isEqualTo("qdrant");
        assertThat(result.get().isEnabled()).isTrue();
    }

    @Test
    void findByIdEmpty() {
        when(turStoreInstanceRepository.findById("missing")).thenReturn(Optional.empty());
        assertThat(adapter.findById("missing")).isEmpty();
    }

    @Test
    void findAllEnabledFiltersByEnabledFlag() {
        when(turStoreInstanceRepository.findAll()).thenReturn(List.of(buildEntity("a", "A", "v", 1),
                buildEntity("b", "B", "v", 0), buildEntity("c", "C", "v", 1)));

        assertThat(adapter.findAllEnabled()).extracting(TurStoreInstanceDomain::id)
                .containsExactly("a", "c");
    }

    @Test
    void domainOmitsCredentialEvenWhenSetOnEntity() {
        // Sensitive fields must not leak via the mapper — same policy as TurLLMInstanceDomain.
        TurStoreInstance entity = buildEntity("store-1", "Qdrant", "qdrant", 1);
        entity.setCredentialEncrypted("super-secret-encrypted-blob");
        entity.setCredential("plaintext-secret");
        when(turStoreInstanceRepository.findById("store-1")).thenReturn(Optional.of(entity));

        TurStoreInstanceDomain domain = adapter.findById("store-1").orElseThrow();

        assertThat(domain.id()).isEqualTo("store-1");
        assertThat(TurStoreInstanceDomain.class.getRecordComponents())
                .extracting(rc -> rc.getName())
                .doesNotContain("credential", "credentialEncrypted");
    }

    private static TurStoreInstance buildEntity(String id, String title, String vendorId,
            int enabled) {
        TurStoreInstance entity = new TurStoreInstance();
        entity.setId(id);
        entity.setTitle(title);
        entity.setEnabled(enabled);
        entity.setUrl("https://example.com");
        TurStoreVendor vendor = new TurStoreVendor();
        vendor.setId(vendorId);
        entity.setTurStoreVendor(vendor);
        return entity;
    }
}
