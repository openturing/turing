/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.sn.TurSNSiteGenAiDomain;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.repository.sn.genai.TurSNSiteGenAiRepository;

/** Unit tests for {@link TurSNSiteGenAiRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurSNSiteGenAiRepositoryAdapterTest {

    @Mock
    private TurSNSiteGenAiRepository turSNSiteGenAiRepository;

    private TurSNSiteGenAiRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurSNSiteGenAiRepositoryAdapter(turSNSiteGenAiRepository,
                Mappers.getMapper(TurSNSiteGenAiDomainMapper.class));
    }

    @Test
    void findByIdProjectsAgentIdAndSitePrompt() {
        TurSNSiteGenAi entity = new TurSNSiteGenAi();
        entity.setId("g-1");
        entity.setSitePrompt("Describe the site to the LLM");
        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        entity.setTurAIAgent(agent);
        when(turSNSiteGenAiRepository.findById("g-1")).thenReturn(Optional.of(entity));

        TurSNSiteGenAiDomain domain = adapter.findById("g-1").orElseThrow();

        assertThat(domain.id()).isEqualTo("g-1");
        assertThat(domain.agentId()).isEqualTo("agent-1");
        assertThat(domain.sitePrompt()).isEqualTo("Describe the site to the LLM");
        assertThat(domain.isConfigured()).isTrue();
    }

    @Test
    void isConfiguredIsFalseWhenAgentIdIsMissing() {
        TurSNSiteGenAi entity = new TurSNSiteGenAi();
        entity.setId("g-1");
        when(turSNSiteGenAiRepository.findById("g-1")).thenReturn(Optional.of(entity));

        TurSNSiteGenAiDomain domain = adapter.findById("g-1").orElseThrow();

        assertThat(domain.agentId()).isNull();
        assertThat(domain.isConfigured()).isFalse();
    }
}
