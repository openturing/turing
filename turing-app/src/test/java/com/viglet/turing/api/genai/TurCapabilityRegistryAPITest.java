/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.api.genai.TurCapabilityRegistryAPI.InstanceCapabilityRow;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService;
import com.viglet.turing.genai.nativeapi.capability.TurCapabilityRegistry;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMInstanceCapability;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

/**
 * T186 / §X.14.f — the per-instance capability matrix endpoint returns the
 * resolved plugin type and only the <em>enabled</em> capability keys (lowercased).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurCapabilityRegistryAPITest {

    @Mock private TurCapabilityRegistry capabilityRegistry;
    @Mock private TurNativeCapabilityService capabilityService;
    @Mock private TurLLMInstanceRepository instanceRepository;
    @Mock private TurGenAiLlmProviderFactory providerFactory;
    @Mock private TurGenAiLlmProvider provider;

    private TurLLMInstanceCapability cap(String key, boolean enabled) {
        TurLLMInstanceCapability row = new TurLLMInstanceCapability();
        row.setCapabilityKey(key);
        row.setEnabled(enabled);
        return row;
    }

    @Test
    void matrixReturnsPluginTypeAndOnlyEnabledKeys() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-1");
        instance.setTitle("OpenAI GPT-4o");
        instance.setEnabled(1);
        when(instanceRepository.findAll()).thenReturn(List.of(instance));
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("OpenAI");
        when(capabilityService.list("inst-1")).thenReturn(List.of(
                cap("OPENAI-WEB-SEARCH", true),
                cap("openai-file-search", false)));

        TurCapabilityRegistryAPI api = new TurCapabilityRegistryAPI(
                capabilityRegistry, capabilityService, instanceRepository, providerFactory);

        List<InstanceCapabilityRow> matrix = api.matrix();

        assertThat(matrix).hasSize(1);
        InstanceCapabilityRow row = matrix.get(0);
        assertThat(row.instanceId()).isEqualTo("inst-1");
        assertThat(row.pluginType()).isEqualTo("openai");
        assertThat(row.enabled()).isTrue();
        // only the enabled capability, lowercased; the disabled one is dropped
        assertThat(row.enabledCapabilityKeys()).containsExactly("openai-web-search");
    }
}
