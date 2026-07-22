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

import com.viglet.turing.genai.provider.llm.TurLlmModelCatalog;
import com.viglet.turing.genai.provider.llm.TurLlmModelKind;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata.TurLlmModelBenchmarks;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata.TurLlmModelPricing;
import com.viglet.turing.genai.provider.llm.TurLlmModelOption;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.service.llm.lifecycle.TurLLMModelLifecycleService.DeprecatedModelUsage;

/**
 * T782 / §LIII.2 — unit tests for deprecated-model detection and GA-replacement
 * ranking over catalog metadata.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurLLMModelLifecycleServiceTest {

    @Mock
    private TurLLMInstanceRepository instanceRepository;
    @Mock
    private TurLlmModelCatalog modelCatalog;

    private TurLLMModelLifecycleService service() {
        return new TurLLMModelLifecycleService(instanceRepository, modelCatalog);
    }

    private static TurLlmModelMetadata meta(Double price, Double intelligence, String status, Boolean deprecated,
            List<String> capabilities) {
        return new TurLlmModelMetadata(
                8000, 4096, null, capabilities, null,
                price == null ? null : new TurLlmModelPricing(price, price, "USD", true, "litellm", null),
                intelligence == null ? null : new TurLlmModelBenchmarks(intelligence, null),
                null, null, null, deprecated, status, null, null);
    }

    private static TurLlmModelOption chat(String id, TurLlmModelMetadata metadata) {
        return new TurLlmModelOption(id, id, TurLlmModelKind.CHAT, metadata);
    }

    private static TurLLMInstance instance(String id, String title, String vendorId, String modelName) {
        TurLLMInstance i = new TurLLMInstance();
        i.setId(id);
        i.setTitle(title);
        i.setModelName(modelName);
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId(vendorId);
        i.setTurLLMVendor(vendor);
        return i;
    }

    @Test
    void flagsDeprecatedModelAndSuggestsCheapestBestGaReplacement() {
        TurLlmModelOption old = chat("old-model", meta(10.0, 60.0, "DEPRECATED", true, List.of("tools")));
        TurLlmModelOption gaBetter = chat("ga-better", meta(8.0, 80.0, "GA", false, List.of("tools", "vision")));
        TurLlmModelOption gaCheapWeaker = chat("ga-cheap", meta(2.0, 50.0, "GA", false, List.of("tools")));
        TurLlmModelOption tooPricey = chat("ga-pricey", meta(20.0, 99.0, "GA", false, List.of("tools")));
        when(modelCatalog.allModels()).thenReturn(java.util.Map.of(
                "openai", List.of(old, gaBetter, gaCheapWeaker, tooPricey)));
        when(instanceRepository.findAll()).thenReturn(List.of(
                instance("i1", "Prod", "OPENAI", "old-model")));

        List<DeprecatedModelUsage> usages = service().findDeprecatedModelsInUse();

        assertThat(usages).singleElement().satisfies(u -> {
            assertThat(u.instanceId()).isEqualTo("i1");
            assertThat(u.modelName()).isEqualTo("old-model");
            assertThat(u.deprecated()).isTrue();
            assertThat(u.status()).isEqualTo("DEPRECATED");
            // ga-better: covers tools, within the $10 budget, highest intelligence.
            assertThat(u.replacementModelId()).isEqualTo("ga-better");
        });
    }

    @Test
    void ignoresInstancesOnCurrentModels() {
        when(modelCatalog.allModels()).thenReturn(java.util.Map.of(
                "openai", List.of(chat("gpt-4o", meta(2.5, 72.0, "GA", false, List.of("tools"))))));
        when(instanceRepository.findAll()).thenReturn(List.of(
                instance("i1", "Prod", "OPENAI", "gpt-4o")));

        assertThat(service().findDeprecatedModelsInUse()).isEmpty();
    }

    @Test
    void flagsRetiredStatusEvenWithoutDeprecatedFlag() {
        TurLlmModelOption retired = chat("retired", meta(1.0, 30.0, "RETIRED", null, List.of()));
        when(modelCatalog.allModels()).thenReturn(java.util.Map.of("openai", List.of(retired)));
        when(instanceRepository.findAll()).thenReturn(List.of(
                instance("i1", "Prod", "OPENAI", "retired")));

        List<DeprecatedModelUsage> usages = service().findDeprecatedModelsInUse();
        assertThat(usages).singleElement().satisfies(u -> {
            assertThat(u.status()).isEqualTo("RETIRED");
            assertThat(u.deprecated()).isFalse();
            assertThat(u.replacementModelId()).isNull(); // no GA sibling to suggest
        });
    }

    @Test
    void ignoresModelsNotInCatalog() {
        when(modelCatalog.allModels()).thenReturn(java.util.Map.of());
        when(instanceRepository.findAll()).thenReturn(List.of(
                instance("i1", "Prod", "OPENAI", "private-finetune")));

        assertThat(service().findDeprecatedModelsInUse()).isEmpty();
    }
}
