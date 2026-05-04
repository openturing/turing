/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.integration;

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

import com.viglet.turing.domain.integration.TurIntegrationInstanceDomain;
import com.viglet.turing.persistence.model.dev.token.TurDevToken;
import com.viglet.turing.persistence.model.integration.TurIntegrationInstance;
import com.viglet.turing.persistence.repository.integration.TurIntegrationInstanceRepository;

/** Unit tests for {@link TurIntegrationInstanceRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurIntegrationInstanceRepositoryAdapterTest {

    @Mock
    private TurIntegrationInstanceRepository turIntegrationInstanceRepository;

    private TurIntegrationInstanceRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurIntegrationInstanceRepositoryAdapter(turIntegrationInstanceRepository,
                Mappers.getMapper(TurIntegrationInstanceDomainMapper.class));
    }

    @Test
    void findByIdProjectsScalarsAndApiTokenId() {
        TurIntegrationInstance entity = new TurIntegrationInstance();
        entity.setId("ii-1");
        entity.setTitle("Slack notifier");
        entity.setEndpoint("https://hooks.slack.com/...");
        entity.setEnabled(1);
        TurDevToken token = new TurDevToken();
        token.setId("tok-1");
        entity.setApiToken(token);
        when(turIntegrationInstanceRepository.findById("ii-1")).thenReturn(Optional.of(entity));

        TurIntegrationInstanceDomain domain = adapter.findById("ii-1").orElseThrow();

        assertThat(domain.id()).isEqualTo("ii-1");
        assertThat(domain.title()).isEqualTo("Slack notifier");
        assertThat(domain.apiTokenId()).isEqualTo("tok-1");
        assertThat(domain.isEnabled()).isTrue();
    }

    @Test
    void findAllEnabledFiltersByEnabledFlag() {
        when(turIntegrationInstanceRepository.findAll())
                .thenReturn(List.of(buildInstance("a", 1), buildInstance("b", 0),
                        buildInstance("c", 1)));

        assertThat(adapter.findAllEnabled()).extracting(TurIntegrationInstanceDomain::id)
                .containsExactly("a", "c");
    }

    private static TurIntegrationInstance buildInstance(String id, int enabled) {
        TurIntegrationInstance entity = new TurIntegrationInstance();
        entity.setId(id);
        entity.setTitle("title-" + id);
        entity.setEndpoint("https://example.com/" + id);
        entity.setEnabled(enabled);
        return entity;
    }
}
