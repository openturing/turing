/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.llm.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.viglet.turing.genai.provider.llm.TurLlmModelCatalog;
import com.viglet.turing.genai.provider.llm.TurLlmModelKind;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata.TurLlmModelPricing;
import com.viglet.turing.genai.provider.llm.TurLlmModelOption;
import com.viglet.turing.service.llm.price.TurLLMPriceService.CatalogPrice;

/**
 * T777 / §LIII.1 — verifies {@link TurLLMPriceCatalogReconciler} flattens the
 * catalog's priced models into {@link CatalogPrice} candidates and delegates the
 * write to {@link TurLLMPriceService}, skips models without pricing, and honours
 * the {@code price-sync.enabled} flag.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurLLMPriceCatalogReconcilerTest {

    @Mock
    private TurLlmModelCatalog modelCatalog;

    @Mock
    private TurLLMPriceService priceService;

    private static TurLlmModelOption priced(String id, TurLlmModelPricing pricing) {
        TurLlmModelMetadata metadata = new TurLlmModelMetadata(
                null, null, null, null, null, pricing, null, null, null, null, null, null, null, null);
        return new TurLlmModelOption(id, id, TurLlmModelKind.CHAT, metadata);
    }

    private TurLLMPriceCatalogReconciler enabled() {
        TurLLMPriceCatalogReconciler reconciler = new TurLLMPriceCatalogReconciler(modelCatalog, priceService);
        ReflectionTestUtils.setField(reconciler, "enabled", true);
        return reconciler;
    }

    @Test
    void syncCollectsOnlyPricedModelsAndDelegates() {
        TurLlmModelOption chat = priced("gpt-4o",
                new TurLlmModelPricing(2.5, 10.0, "USD", true, "litellm", "2026-07-20"));
        TurLlmModelOption embedNoPrice =
                new TurLlmModelOption("text-embedding-3-small", "text-embedding-3-small",
                        TurLlmModelKind.EMBEDDING, null);
        when(modelCatalog.allModels()).thenReturn(Map.of("openai", List.of(chat, embedNoPrice)));
        when(priceService.reconcileCatalogPrices(anyCollection())).thenReturn(1);

        enabled().syncPricesFromCatalog();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<CatalogPrice>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(priceService).reconcileCatalogPrices(captor.capture());
        // Only the priced chat model — the embedding model has no pricing metadata.
        assertThat(captor.getValue()).singleElement().satisfies(c -> {
            assertThat(c.vendorId()).isEqualTo("openai");
            assertThat(c.modelName()).isEqualTo("gpt-4o");
            assertThat(c.inputPerMillion()).isEqualTo(2.5);
            assertThat(c.outputPerMillion()).isEqualTo(10.0);
            assertThat(c.indicative()).isTrue();
            assertThat(c.provenance()).isEqualTo("litellm");
            assertThat(c.lastVerified()).isEqualTo("2026-07-20");
        });
    }

    @Test
    void syncDefaultsMissingPriceSideToZero() {
        TurLlmModelOption inputOnly = priced("weird-model",
                new TurLlmModelPricing(1.0, null, null, false, null, null));
        when(modelCatalog.allModels()).thenReturn(Map.of("openai", List.of(inputOnly)));
        when(priceService.reconcileCatalogPrices(anyCollection())).thenReturn(1);

        enabled().syncPricesFromCatalog();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<CatalogPrice>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(priceService).reconcileCatalogPrices(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(c -> {
            assertThat(c.inputPerMillion()).isEqualTo(1.0);
            assertThat(c.outputPerMillion()).isZero();
        });
    }

    @Test
    void syncIsNoOpWhenDisabled() {
        // enabled defaults to false without the @Value injection.
        new TurLLMPriceCatalogReconciler(modelCatalog, priceService).syncPricesFromCatalog();
        verify(priceService, never()).reconcileCatalogPrices(anyCollection());
    }

    @Test
    void syncIsNoOpWhenNoPricedModels() {
        lenient().when(modelCatalog.allModels()).thenReturn(Map.of());
        enabled().syncPricesFromCatalog();
        verify(priceService, never()).reconcileCatalogPrices(anyCollection());
    }
}
