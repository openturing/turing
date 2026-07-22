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

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.viglet.turing.genai.provider.llm.TurLlmModelCatalog;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata;
import com.viglet.turing.genai.provider.llm.TurLlmModelOption;
import com.viglet.turing.service.llm.price.TurLLMPriceService.CatalogPrice;

import lombok.extern.slf4j.Slf4j;

/**
 * T777 / §LIII.1 (Block BE) — a scheduled reconciler that auto-seeds and refreshes
 * the {@code tur_llm_price} table from the public model catalog's indicative
 * {@code pricing}. Before this the price table was manual-only, so a model with no
 * hand-entered row billed at {@code $0} — the reconciler closes that gap so
 * {@link TurLLMPriceService#computeCost} and {@code TurLLMTokenUsage.costUsd}
 * populate automatically for well-known models.
 *
 * <p>It only ever writes {@link com.viglet.turing.persistence.model.llm.TurLLMPriceSource#CATALOG
 * CATALOG} rows: an operator's {@code MANUAL} (negotiated / hand-edited) rate is
 * never overwritten (see {@link TurLLMPriceService#reconcileCatalogPrices}). Runs
 * off the boot path on a TTL (mirroring the catalog refresh cadence) so a running
 * instance picks up catalog price changes without a redeploy; disabled with
 * {@code turing.model-catalog.price-sync.enabled=false}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurLLMPriceCatalogReconciler {

    private final TurLlmModelCatalog modelCatalog;
    private final TurLLMPriceService priceService;

    @Value("${turing.model-catalog.price-sync.enabled:true}")
    private boolean enabled;

    public TurLLMPriceCatalogReconciler(TurLlmModelCatalog modelCatalog, TurLLMPriceService priceService) {
        this.modelCatalog = modelCatalog;
        this.priceService = priceService;
    }

    /**
     * Walks the effective catalog and reconciles every model that carries an
     * indicative price into the price table. Best-effort: a reconcile failure is
     * logged and never propagated (the schedule keeps running). Runs after an
     * initial delay so it never blocks startup, and after the catalog itself has
     * had a chance to refresh (its initial delay is shorter).
     */
    @Scheduled(
            initialDelayString = "${turing.model-catalog.price-sync.initial-delay-ms:15000}",
            fixedDelayString = "${turing.model-catalog.price-sync.interval-ms:21600000}")
    void syncPricesFromCatalog() {
        if (!enabled) {
            return;
        }
        try {
            List<CatalogPrice> candidates = collectPricedModels();
            if (candidates.isEmpty()) {
                return;
            }
            int written = priceService.reconcileCatalogPrices(candidates);
            if (written > 0) {
                log.info("Reconciled {} catalog price row(s) from the model catalog ({} priced models found)",
                        written, candidates.size());
            }
        } catch (Exception e) {
            log.debug("Catalog price reconciliation failed, keeping current prices: {}", e.getMessage());
        }
    }

    /** Flattens the catalog to the priced models the reconciler cares about. */
    private List<CatalogPrice> collectPricedModels() {
        List<CatalogPrice> candidates = new ArrayList<>();
        modelCatalog.allModels().forEach((vendor, options) -> {
            for (TurLlmModelOption option : options) {
                CatalogPrice candidate = toCandidate(vendor, option);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        });
        return candidates;
    }

    /**
     * Builds a {@link CatalogPrice} for a catalog option that carries a usable
     * indicative price, or {@code null} when the option has no metadata / no price.
     */
    private CatalogPrice toCandidate(String vendor, TurLlmModelOption option) {
        TurLlmModelMetadata metadata = option.metadata();
        if (metadata == null || metadata.pricing() == null) {
            return null;
        }
        TurLlmModelMetadata.TurLlmModelPricing pricing = metadata.pricing();
        Double in = pricing.inputPer1M();
        Double out = pricing.outputPer1M();
        if (in == null && out == null) {
            return null;
        }
        return new CatalogPrice(
                vendor,
                option.id(),
                in == null ? 0.0 : in,
                out == null ? 0.0 : out,
                pricing.currency(),
                Boolean.TRUE.equals(pricing.indicative()),
                pricing.source(),
                pricing.lastVerified());
    }
}
