/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.persistence.model.llm.TurLLMInstanceCapability;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceCapabilityRepository;

@ExtendWith(MockitoExtension.class)
class TurNativeCapabilityServiceTest {

    @Mock
    private TurLLMInstanceCapabilityRepository repository;

    @InjectMocks
    private TurNativeCapabilityService service;

    private static TurLLMInstanceCapability row(String key, boolean enabled, String configJson) {
        TurLLMInstanceCapability capability = new TurLLMInstanceCapability();
        capability.setInstanceId("inst-1");
        capability.setCapabilityKey(key);
        capability.setEnabled(enabled);
        capability.setConfigJson(configJson);
        return capability;
    }

    @Test
    void hasCapabilityOnlyTrueWhenRowEnabled() {
        when(repository.findByInstanceId("inst-1")).thenReturn(List.of(
                row("openai-web-search", true, null),
                row("openai-file-search", false, null)));

        assertThat(service.hasCapability("inst-1", TurNativeCapability.OPENAI_WEB_SEARCH)).isTrue();
        assertThat(service.hasCapability("inst-1", TurNativeCapability.OPENAI_FILE_SEARCH)).isFalse();
        assertThat(service.hasCapability("inst-1", TurNativeCapability.OPENAI_MCP)).isFalse();
    }

    @Test
    void enabledForFiltersDisabledUnknownAndOtherVendor() {
        when(repository.findByInstanceId("inst-1")).thenReturn(List.of(
                row("openai-web-search", true, null),
                row("openai-file-search", false, null),       // disabled
                row("anthropic-web-search", true, null),       // unknown key (not in enum yet)
                row("openai-code-interpreter", true, "{}")));

        List<EnabledCapability> enabled = service.enabledFor("inst-1", "openai");

        assertThat(enabled).extracting(EnabledCapability::capability)
                .containsExactlyInAnyOrder(TurNativeCapability.OPENAI_WEB_SEARCH,
                        TurNativeCapability.OPENAI_CODE_INTERPRETER);
    }

    @Test
    void enabledForExcludesCapabilitiesOfADifferentPlugin() {
        when(repository.findByInstanceId("inst-1")).thenReturn(List.of(
                row("openai-web-search", true, null)));

        assertThat(service.enabledFor("inst-1", "anthropic")).isEmpty();
    }

    @Test
    void upsertCreatesWhenAbsent() {
        when(repository.findByInstanceIdAndCapabilityKey("inst-1", "openai-web-search"))
                .thenReturn(Optional.empty());
        when(repository.save(any(TurLLMInstanceCapability.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TurLLMInstanceCapability saved = service.upsert("inst-1",
                TurNativeCapability.OPENAI_WEB_SEARCH, true, "{\"x\":1}");

        assertThat(saved.getInstanceId()).isEqualTo("inst-1");
        assertThat(saved.getCapabilityKey()).isEqualTo("openai-web-search");
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getConfigJson()).isEqualTo("{\"x\":1}");
    }

    @Test
    void deleteIsNoOpWhenAbsent() {
        when(repository.findByInstanceIdAndCapabilityKey("inst-1", "openai-mcp"))
                .thenReturn(Optional.empty());

        service.delete("inst-1", TurNativeCapability.OPENAI_MCP);

        verify(repository, never()).delete(any());
    }
}
