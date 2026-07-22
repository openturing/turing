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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.llm.TurLLMPrice;
import com.viglet.turing.persistence.model.llm.TurLLMPriceSource;
import com.viglet.turing.persistence.repository.llm.TurLLMPriceRepository;
import com.viglet.turing.service.llm.price.TurLLMPriceService.CatalogPrice;

/**
 * T289 / §XVI.1 — unit tests for the cost computation and upsert behaviour of
 * {@link TurLLMPriceService}. {@code rates()} never touches the self-proxy, so
 * an inner instance with a {@code null} self backs the outer's
 * {@code computeCost} delegation without a Spring context.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurLLMPriceServiceTest {

    @Mock
    private TurLLMPriceRepository priceRepository;

    private TurLLMPriceService service;

    @BeforeEach
    void setUp() {
        TurLLMPriceService inner = new TurLLMPriceService(priceRepository, null);
        service = new TurLLMPriceService(priceRepository, inner);
    }

    @Test
    void shouldComputeCostFromConfiguredRates() {
        TurLLMPrice price = new TurLLMPrice();
        price.setVendorId("openai");
        price.setModelName("gpt-4o");
        price.setInputPricePerMillion(2.5);
        price.setOutputPricePerMillion(10.0);
        when(priceRepository.findByVendorIdAndModelName("openai", "gpt-4o"))
                .thenReturn(Optional.of(price));

        // 1000 input @ $2.5/M + 500 output @ $10/M = 0.0025 + 0.005 = 0.0075
        double cost = service.computeCost("openai", "gpt-4o", 1000, 500);

        assertThat(cost).isEqualTo(0.0075, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    void shouldReturnZeroForUnknownModel() {
        when(priceRepository.findByVendorIdAndModelName("ollama", "llama3"))
                .thenReturn(Optional.empty());

        assertThat(service.computeCost("ollama", "llama3", 5000, 5000)).isZero();
    }

    @Test
    void shouldReturnZeroWhenVendorOrModelIsNull() {
        assertThat(service.computeCost(null, "gpt-4o", 100, 100)).isZero();
        assertThat(service.computeCost("openai", null, 100, 100)).isZero();
    }

    @Test
    void shouldUpsertExistingRowOnSave() {
        TurLLMPrice existing = new TurLLMPrice();
        existing.setId("price-1");
        existing.setVendorId("openai");
        existing.setModelName("gpt-4o");
        existing.setInputPricePerMillion(2.5);
        existing.setOutputPricePerMillion(10.0);
        when(priceRepository.findByVendorIdAndModelName("openai", "gpt-4o"))
                .thenReturn(Optional.of(existing));
        when(priceRepository.save(any(TurLLMPrice.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TurLLMPrice update = new TurLLMPrice();
        update.setVendorId("openai");
        update.setModelName("gpt-4o");
        update.setInputPricePerMillion(3.0);
        update.setOutputPricePerMillion(12.0);
        update.setCurrency("");

        service.save(update);

        ArgumentCaptor<TurLLMPrice> captor = ArgumentCaptor.forClass(TurLLMPrice.class);
        verify(priceRepository).save(captor.capture());
        TurLLMPrice saved = captor.getValue();
        // Upserted onto the existing surrogate id, with the new rates and a
        // defaulted currency.
        assertThat(saved.getId()).isEqualTo("price-1");
        assertThat(saved.getInputPricePerMillion()).isEqualTo(3.0);
        assertThat(saved.getOutputPricePerMillion()).isEqualTo(12.0);
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void saveMarksRowManualByDefault() {
        // T777: an operator save (no explicit source) is MANUAL so the catalog
        // reconciler can never overwrite it.
        when(priceRepository.findByVendorIdAndModelName("openai", "gpt-4o"))
                .thenReturn(Optional.empty());
        when(priceRepository.save(any(TurLLMPrice.class))).thenAnswer(inv -> inv.getArgument(0));

        TurLLMPrice update = new TurLLMPrice();
        update.setVendorId("openai");
        update.setModelName("gpt-4o");
        update.setInputPricePerMillion(2.5);
        update.setOutputPricePerMillion(10.0);

        service.save(update);

        ArgumentCaptor<TurLLMPrice> captor = ArgumentCaptor.forClass(TurLLMPrice.class);
        verify(priceRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(TurLLMPriceSource.MANUAL);
    }

    @Test
    void reconcileCatalogCreatesCatalogRowWhenMissing() {
        // T777: a priced catalog model with no row is auto-seeded as CATALOG.
        when(priceRepository.findByVendorIdAndModelName("openai", "gpt-4o"))
                .thenReturn(Optional.empty());
        when(priceRepository.save(any(TurLLMPrice.class))).thenAnswer(inv -> inv.getArgument(0));

        int written = service.reconcileCatalogPrices(List.of(
                new CatalogPrice("openai", "gpt-4o", 2.5, 10.0, "USD", true, "litellm", "2026-07-20")));

        assertThat(written).isEqualTo(1);
        ArgumentCaptor<TurLLMPrice> captor = ArgumentCaptor.forClass(TurLLMPrice.class);
        verify(priceRepository).save(captor.capture());
        TurLLMPrice saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo(TurLLMPriceSource.CATALOG);
        assertThat(saved.getInputPricePerMillion()).isEqualTo(2.5);
        assertThat(saved.getOutputPricePerMillion()).isEqualTo(10.0);
        assertThat(saved.isIndicative()).isTrue();
        assertThat(saved.getPriceProvenance()).isEqualTo("litellm");
        assertThat(saved.getLastVerified()).isEqualTo("2026-07-20");
    }

    @Test
    void reconcileCatalogNeverOverwritesManualRow() {
        // T777: an operator's negotiated (MANUAL) rate always wins.
        TurLLMPrice manual = new TurLLMPrice();
        manual.setVendorId("openai");
        manual.setModelName("gpt-4o");
        manual.setInputPricePerMillion(1.0);
        manual.setSource(TurLLMPriceSource.MANUAL);
        when(priceRepository.findByVendorIdAndModelName("openai", "gpt-4o"))
                .thenReturn(Optional.of(manual));

        int written = service.reconcileCatalogPrices(List.of(
                new CatalogPrice("openai", "gpt-4o", 2.5, 10.0, "USD", true, "litellm", "2026-07-20")));

        assertThat(written).isZero();
        verify(priceRepository, never()).save(any(TurLLMPrice.class));
        assertThat(manual.getInputPricePerMillion()).isEqualTo(1.0);
    }

    @Test
    void reconcileCatalogRefreshesExistingCatalogRow() {
        // T777: a row the reconciler itself owns (CATALOG) is refreshed in place.
        TurLLMPrice existing = new TurLLMPrice();
        existing.setId("price-1");
        existing.setVendorId("openai");
        existing.setModelName("gpt-4o");
        existing.setInputPricePerMillion(2.5);
        existing.setSource(TurLLMPriceSource.CATALOG);
        when(priceRepository.findByVendorIdAndModelName("openai", "gpt-4o"))
                .thenReturn(Optional.of(existing));
        when(priceRepository.save(any(TurLLMPrice.class))).thenAnswer(inv -> inv.getArgument(0));

        int written = service.reconcileCatalogPrices(List.of(
                new CatalogPrice("openai", "gpt-4o", 3.0, 12.0, "USD", true, "litellm", "2026-07-21")));

        assertThat(written).isEqualTo(1);
        ArgumentCaptor<TurLLMPrice> captor = ArgumentCaptor.forClass(TurLLMPrice.class);
        verify(priceRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("price-1");
        assertThat(captor.getValue().getInputPricePerMillion()).isEqualTo(3.0);
    }

    @Test
    void reconcileCatalogSkipsBlankVendorOrModel() {
        int written = service.reconcileCatalogPrices(List.of(
                new CatalogPrice("", "gpt-4o", 2.5, 10.0, "USD", false, null, null),
                new CatalogPrice("openai", " ", 2.5, 10.0, "USD", false, null, null)));

        assertThat(written).isZero();
        verify(priceRepository, never()).save(any(TurLLMPrice.class));
    }
}
