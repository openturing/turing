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

import com.viglet.turing.domain.llm.TurLLMInstanceDomain;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

/**
 * Unit tests for {@link TurLLMInstanceRepositoryAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class TurLLMInstanceRepositoryAdapterTest {

    @Mock
    private TurLLMInstanceRepository turLLMInstanceRepository;

    private TurLLMInstanceRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurLLMInstanceRepositoryAdapter(turLLMInstanceRepository,
                Mappers.getMapper(TurLLMInstanceDomainMapper.class));
    }

    @Test
    void findByIdReturnsMappedDomain() {
        TurLLMInstance entity = buildEntity("llm-1", "OpenAI GPT", "vendor-1", 1);
        entity.setApiKeyEncrypted("secret-encrypted");
        when(turLLMInstanceRepository.findById("llm-1")).thenReturn(Optional.of(entity));

        Optional<TurLLMInstanceDomain> result = adapter.findById("llm-1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("llm-1");
        assertThat(result.get().title()).isEqualTo("OpenAI GPT");
        assertThat(result.get().vendorId()).isEqualTo("vendor-1");
        assertThat(result.get().isEnabled()).isTrue();
    }

    @Test
    void findByIdEmptyDelegatesEmpty() {
        when(turLLMInstanceRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(adapter.findById("missing")).isEmpty();
    }

    @Test
    void findAllReturnsMappedDomainList() {
        when(turLLMInstanceRepository.findAll())
                .thenReturn(List.of(buildEntity("a", "A", "v", 1), buildEntity("b", "B", "v", 0)));

        List<TurLLMInstanceDomain> result = adapter.findAll();

        assertThat(result).extracting(TurLLMInstanceDomain::id).containsExactly("a", "b");
    }

    @Test
    void findAllEnabledFiltersByEnabledFlag() {
        when(turLLMInstanceRepository.findAll())
                .thenReturn(List.of(buildEntity("a", "A", "v", 1), buildEntity("b", "B", "v", 0),
                        buildEntity("c", "C", "v", 1)));

        List<TurLLMInstanceDomain> result = adapter.findAllEnabled();

        assertThat(result).extracting(TurLLMInstanceDomain::id).containsExactly("a", "c");
    }

    @Test
    void domainOmitsApiKeyEvenWhenSetOnEntity() {
        // Sensitive fields must not leak via the mapper.
        TurLLMInstance entity = buildEntity("llm-1", "Test", "vendor", 1);
        entity.setApiKeyEncrypted("super-secret-encrypted-blob");
        entity.setApiKey("plaintext-secret");
        when(turLLMInstanceRepository.findById("llm-1")).thenReturn(Optional.of(entity));

        TurLLMInstanceDomain domain = adapter.findById("llm-1").orElseThrow();

        // Domain record has no apiKey accessor at all — verifying via reflection would be brittle;
        // we assert the public surface exposes only the safe fields.
        assertThat(domain.id()).isEqualTo("llm-1");
        assertThat(domain.title()).isEqualTo("Test");
        assertThat(TurLLMInstanceDomain.class.getRecordComponents())
                .extracting(rc -> rc.getName())
                .doesNotContain("apiKey", "apiKeyEncrypted");
    }

    private static TurLLMInstance buildEntity(String id, String title, String vendorId,
            int enabled) {
        TurLLMInstance entity = new TurLLMInstance();
        entity.setId(id);
        entity.setTitle(title);
        entity.setEnabled(enabled);
        entity.setUrl("https://example.com");
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId(vendorId);
        entity.setTurLLMVendor(vendor);
        return entity;
    }
}
