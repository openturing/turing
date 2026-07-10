/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.onstartup.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * Unit tests for the T637 demo-persona seeding that rides on the fresh default
 * agent provisioned by {@link TurLLMInstanceOnStartup}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurLLMInstanceOnStartupPersonaSeedTest {

    @Mock private Environment environment;
    @Mock private TurLLMInstanceRepository turLLMInstanceRepository;
    @Mock private TurLLMVendorRepository turLLMVendorRepository;
    @Mock private TurAIAgentRepository turAIAgentRepository;
    @Mock private TurPersonaRepository turPersonaRepository;
    @Mock private TurSecretCryptoService turSecretCryptoService;
    @Mock private TurGlobalSettingsService turGlobalSettingsService;
    @Mock private ApplicationArguments args;

    private TurLLMInstanceOnStartup runner;

    @BeforeEach
    void setUp() {
        runner = new TurLLMInstanceOnStartup(environment, turLLMInstanceRepository,
                turLLMVendorRepository, turAIAgentRepository, turPersonaRepository,
                turSecretCryptoService, turGlobalSettingsService);

        // Fresh install: enabled, key present, no LLM instance, no agent, no persona.
        lenient().when(environment.getProperty("turing.startup.default-llm.enabled",
                Boolean.class, Boolean.TRUE)).thenReturn(Boolean.TRUE);
        lenient().when(environment.getProperty("turing.startup.default-agent.seed-personas.enabled",
                Boolean.class, Boolean.TRUE)).thenReturn(Boolean.TRUE);
        lenient().when(environment.getProperty("OPENAI_API_KEY")).thenReturn("sk-test");
        lenient().when(environment.getProperty(eq("turing.startup.default-llm.model"), any(String.class)))
                .thenAnswer(inv -> inv.getArgument(1));
        lenient().when(turSecretCryptoService.encrypt(any())).thenReturn("enc");
        lenient().when(turLLMInstanceRepository.findAll()).thenReturn(List.of());
        lenient().when(turLLMVendorRepository.findById("OPENAI"))
                .thenReturn(java.util.Optional.of(new TurLLMVendor()));
        lenient().when(turAIAgentRepository.findAll()).thenReturn(List.of());
        lenient().when(turGlobalSettingsService.getDefaultAiAgentId()).thenReturn(null);
    }

    @Test
    void seedsThreeDemoPersonasOntoTheFreshDefaultAgent() {
        when(turPersonaRepository.count()).thenReturn(0L);

        runner.run(args);

        verify(turPersonaRepository, times(3)).save(any(TurPersona.class));

        ArgumentCaptor<TurAIAgent> agentCaptor = ArgumentCaptor.forClass(TurAIAgent.class);
        verify(turAIAgentRepository, org.mockito.Mockito.atLeastOnce()).save(agentCaptor.capture());
        TurAIAgent saved = agentCaptor.getValue();

        List<String> names = saved.getPersonas().stream().map(TurPersona::getName).sorted().toList();
        assertThat(names).containsExactly("Enterprise Buyer", "First-time User", "Skeptical Developer");

        // Stable ids the T638 demo playground hardcodes — must not drift.
        List<String> ids = saved.getPersonas().stream().map(TurPersona::getId).sorted().toList();
        assertThat(ids).containsExactly(
                "persona-enterprise-buyer", "persona-first-time-user", "persona-skeptical-developer");

        // Mix of speaker + audience: at least one speaker-usable and all audience-usable.
        assertThat(saved.getPersonas()).anyMatch(TurPersona::isUsableAsSpeaker);
        assertThat(saved.getPersonas()).allMatch(TurPersona::isUsableAsAudience);
        // Neutral baseline preserved — no default persona is forced.
        assertThat(saved.getDefaultPersona()).isNull();
        // The pure reader carries an audience facet the content-fit path reads.
        TurPersona firstTimer = saved.getPersonas().stream()
                .filter(p -> "First-time User".equals(p.getName())).findFirst().orElseThrow();
        assertThat(firstTimer.getPersonaKind()).isEqualTo(TurPersonaKind.AUDIENCE);
        assertThat(firstTimer.getAudience()).isNotNull();
        assertThat(firstTimer.getAudience().getPrimaryLanguage()).isEqualTo("en");
    }

    @Test
    void skipsSeedingWhenDisabled() {
        when(environment.getProperty("turing.startup.default-agent.seed-personas.enabled",
                Boolean.class, Boolean.TRUE)).thenReturn(Boolean.FALSE);

        runner.run(args);

        verify(turPersonaRepository, times(0)).save(any(TurPersona.class));
    }

    @Test
    void skipsSeedingWhenPersonasAlreadyExist() {
        when(turPersonaRepository.count()).thenReturn(5L);

        runner.run(args);

        verify(turPersonaRepository, times(0)).save(any(TurPersona.class));
    }
}
