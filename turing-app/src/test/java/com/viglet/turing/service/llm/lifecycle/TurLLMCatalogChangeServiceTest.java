/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.llm.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.provider.llm.TurCatalogChanges;
import com.viglet.turing.genai.provider.llm.TurCatalogChanges.TurCatalogChangeEntry;
import com.viglet.turing.genai.provider.llm.TurLlmModelCatalog;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.service.llm.lifecycle.TurLLMCatalogChangeService.ChangeNotification;
import com.viglet.turing.service.llm.lifecycle.TurLLMCatalogChangeService.ChangeType;

/**
 * T788 / §LIII.4 — unit tests for change-feed notifications on in-use models.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurLLMCatalogChangeServiceTest {

    @Mock
    private TurLLMInstanceRepository instanceRepository;
    @Mock
    private TurLlmModelCatalog modelCatalog;

    private TurLLMCatalogChangeService service() {
        return new TurLLMCatalogChangeService(instanceRepository, modelCatalog);
    }

    private static TurLLMInstance instance(String vendorId, String modelName) {
        TurLLMInstance i = new TurLLMInstance();
        i.setModelName(modelName);
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId(vendorId);
        i.setTurLLMVendor(vendor);
        return i;
    }

    private static TurCatalogChangeEntry entry(String vendor, String id, String detail) {
        return new TurCatalogChangeEntry(vendor, id, "CHAT", id, detail);
    }

    @Test
    void flagsRemovedInUseModelAsSuperseded() {
        when(instanceRepository.findAll()).thenReturn(List.of(instance("ANTHROPIC", "claude-3-5-haiku-20241022")));
        when(modelCatalog.catalogChanges()).thenReturn(new TurCatalogChanges(
                List.of(entry("anthropic", "claude-haiku-4-5-20251001", null)),
                List.of(entry("anthropic", "claude-3-5-haiku-20241022", null)),
                List.of()));

        List<ChangeNotification> out = service().notificationsForUsedModels();

        assertThat(out).singleElement().satisfies(n -> {
            assertThat(n.type()).isEqualTo(ChangeType.SUPERSEDED);
            assertThat(n.modelId()).isEqualTo("claude-3-5-haiku-20241022");
            assertThat(n.replacementCandidates()).contains("claude-haiku-4-5-20251001");
        });
    }

    @Test
    void flagsRemovedWithNoSameVendorAddedAsRemoved() {
        when(instanceRepository.findAll()).thenReturn(List.of(instance("OPENAI", "gpt-old")));
        when(modelCatalog.catalogChanges()).thenReturn(new TurCatalogChanges(
                List.of(entry("anthropic", "claude-new", null)), // different vendor
                List.of(entry("openai", "gpt-old", null)),
                List.of()));

        assertThat(service().notificationsForUsedModels()).singleElement().satisfies(n -> {
            assertThat(n.type()).isEqualTo(ChangeType.REMOVED);
            assertThat(n.replacementCandidates()).isEmpty();
        });
    }

    @Test
    void flagsChangedInUseModel() {
        when(instanceRepository.findAll()).thenReturn(List.of(instance("OPENAI", "gpt-4o")));
        when(modelCatalog.catalogChanges()).thenReturn(new TurCatalogChanges(
                List.of(), List.of(),
                List.of(entry("openai", "gpt-4o", "input price 2.5 -> 3.0"))));

        assertThat(service().notificationsForUsedModels()).singleElement().satisfies(n -> {
            assertThat(n.type()).isEqualTo(ChangeType.CHANGED);
            assertThat(n.detail()).isEqualTo("input price 2.5 -> 3.0");
        });
    }

    @Test
    void ignoresChangesToModelsNotInUse() {
        when(instanceRepository.findAll()).thenReturn(List.of(instance("OPENAI", "gpt-4o")));
        when(modelCatalog.catalogChanges()).thenReturn(new TurCatalogChanges(
                List.of(), List.of(entry("anthropic", "claude-x", null)), List.of()));

        assertThat(service().notificationsForUsedModels()).isEmpty();
    }

    @Test
    void emptyWhenNoInstances() {
        when(instanceRepository.findAll()).thenReturn(List.of());
        assertThat(service().notificationsForUsedModels()).isEmpty();
    }
}
