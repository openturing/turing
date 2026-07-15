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
import static org.mockito.Mockito.never;
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
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * Unit tests for the T655 import-aware default-agent provisioning in
 * {@link TurLLMInstanceOnStartup}: create a generic agent on a truly fresh
 * install, but ADOPT an imported agent (e.g. the demo's agent + personas) as the
 * global default and wire the env-key LLM onto it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurLLMInstanceOnStartupAgentProvisionTest {

    @Mock private Environment environment;
    @Mock private TurLLMInstanceRepository turLLMInstanceRepository;
    @Mock private TurLLMVendorRepository turLLMVendorRepository;
    @Mock private TurAIAgentRepository turAIAgentRepository;
    @Mock private TurSecretCryptoService turSecretCryptoService;
    @Mock private TurGlobalSettingsService turGlobalSettingsService;
    @Mock private ApplicationArguments args;

    private TurLLMInstanceOnStartup runner;

    @BeforeEach
    void setUp() {
        runner = new TurLLMInstanceOnStartup(environment, turLLMInstanceRepository,
                turLLMVendorRepository, turAIAgentRepository, turSecretCryptoService,
                turGlobalSettingsService);

        when(environment.getProperty("turing.startup.default-llm.enabled",
                Boolean.class, Boolean.TRUE)).thenReturn(Boolean.TRUE);
        when(environment.getProperty("OPENAI_API_KEY")).thenReturn("sk-test");
        when(environment.getProperty(eq("turing.startup.default-llm.model"), any(String.class)))
                .thenAnswer(inv -> inv.getArgument(1));
        when(turSecretCryptoService.encrypt(any())).thenReturn("enc");
        when(turLLMInstanceRepository.findAll()).thenReturn(List.of());
        when(turLLMVendorRepository.findById("OPENAI"))
                .thenReturn(java.util.Optional.of(new TurLLMVendor()));
        when(turGlobalSettingsService.getDefaultAiAgentId()).thenReturn(null);
    }

    @Test
    void freshInstallCreatesGenericDefaultAgentAndWiresLlm() {
        when(turAIAgentRepository.findAll()).thenReturn(List.of());

        runner.run(args);

        ArgumentCaptor<TurAIAgent> captor = ArgumentCaptor.forClass(TurAIAgent.class);
        verify(turAIAgentRepository).save(captor.capture());
        TurAIAgent created = captor.getValue();
        assertThat(created.getTitle()).isEqualTo("Default Agent");
        assertThat(created.getLlmInstances()).hasSize(1);
        // The auto-provisioned default agent backs the zero-config public demo's
        // SN chat (T622 fallback), so it ships grounded to the site's content.
        assertThat(created.getGroundingMode())
                .isEqualTo(com.viglet.turing.persistence.model.agent.TurAgentGroundingMode.STRICT_RAG);
        verify(turGlobalSettingsService).updateDefaultAiAgentId(any());
    }

    @Test
    void importedAgentIsAdoptedAsDefaultAndGetsLlmWired() {
        TurAIAgent imported = new TurAIAgent();
        imported.setId("imported-1");
        imported.setTitle("Turing Demo Assistant");
        when(turAIAgentRepository.findAll()).thenReturn(List.of(imported));

        runner.run(args);

        // Adopted as the global default (not a freshly-created generic agent).
        verify(turGlobalSettingsService).updateDefaultAiAgentId("imported-1");
        // The env-key LLM was wired onto the imported agent.
        assertThat(imported.getLlmInstances()).hasSize(1);
        verify(turAIAgentRepository).save(imported);
    }

    @Test
    void geminiKeyProvisionsGeminiInstanceWithDefaultModel() {
        // No OpenAI key, but GEMINI_API_KEY is set → Gemini vendor, default model.
        when(environment.getProperty("OPENAI_API_KEY")).thenReturn(null);
        when(environment.getProperty("GEMINI_API_KEY")).thenReturn("gm-test");
        when(environment.getProperty("turing.startup.default-llm.model")).thenReturn(null);
        when(turLLMVendorRepository.findById("GEMINI"))
                .thenReturn(java.util.Optional.of(new TurLLMVendor()));
        when(turAIAgentRepository.findAll()).thenReturn(List.of());

        runner.run(args);

        ArgumentCaptor<TurLLMInstance> captor = ArgumentCaptor.forClass(TurLLMInstance.class);
        verify(turLLMInstanceRepository).save(captor.capture());
        TurLLMInstance created = captor.getValue();
        assertThat(created.getModelName()).isEqualTo("gemini-3.1-flash-lite");
        assertThat(created.getTitle()).isEqualTo("Google Gemini (default)");
        verify(turGlobalSettingsService).updateDefaultLlmId(any());
    }

    @Test
    void sharedModelOverrideAppliesToGemini() {
        when(environment.getProperty("OPENAI_API_KEY")).thenReturn(null);
        when(environment.getProperty("GEMINI_API_KEY")).thenReturn("gm-test");
        when(environment.getProperty("turing.startup.default-llm.model")).thenReturn("gemini-3.1-pro");
        when(turLLMVendorRepository.findById("GEMINI"))
                .thenReturn(java.util.Optional.of(new TurLLMVendor()));
        when(turAIAgentRepository.findAll()).thenReturn(List.of());

        runner.run(args);

        ArgumentCaptor<TurLLMInstance> captor = ArgumentCaptor.forClass(TurLLMInstance.class);
        verify(turLLMInstanceRepository).save(captor.capture());
        assertThat(captor.getValue().getModelName()).isEqualTo("gemini-3.1-pro");
    }

    @Test
    void importedAgentAlreadyWiredIsNotDoubleWired() {
        TurAIAgent imported = new TurAIAgent();
        imported.setId("imported-1");
        imported.getLlmInstances().add(new TurLLMInstance()); // already has an LLM
        when(turAIAgentRepository.findAll()).thenReturn(List.of(imported));

        runner.run(args);

        verify(turGlobalSettingsService).updateDefaultAiAgentId("imported-1");
        assertThat(imported.getLlmInstances()).hasSize(1); // unchanged
        verify(turAIAgentRepository, never()).save(imported); // no re-wire save
    }
}
