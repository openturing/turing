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

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.llm.TurLLMPrice;
import com.viglet.turing.persistence.repository.llm.TurLLMPriceRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T289 / §XVI.1 — looks up the per-model price table and turns per-turn token
 * counts into a USD cost. Provider-agnostic: an unknown model (no row) or a
 * local / embedded model (zero-priced row) yields {@code 0.0}, which is what
 * keeps the "$0 fully local" story honest — the cost layer keys off price, not
 * engine.
 *
 * <p>The {@code (vendorId, modelName) → rates} lookup is on the chat hot path
 * (one resolution per turn), so it is {@code @Cacheable("turLlmPrice")}.
 * {@link #computeCost} routes through the {@code @Lazy} self-proxy so the cache
 * advice actually fires (a {@code this.rates(...)} call would bypass it — see
 * {@link com.viglet.turing.genai.flow.TurChatFlowStaticPromptCache}). Every
 * mutation evicts the whole cache so an operator's price edit takes effect on
 * the next turn.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurLLMPriceService {

    /** Cache name for the {@code (vendorId, modelName) → rates} lookup. */
    public static final String PRICE_CACHE = "turLlmPrice";

    private final TurLLMPriceRepository priceRepository;
    private final TurLLMPriceService self;

    /**
     * Per-million-token rates resolved for a model. A {@code null} row resolves
     * to {@code ZERO} so callers never branch on absence.
     */
    public record Rates(double inputPerMillion, double outputPerMillion) {
        public static final Rates ZERO = new Rates(0.0, 0.0);
    }

    public TurLLMPriceService(TurLLMPriceRepository priceRepository,
            @Lazy TurLLMPriceService self) {
        this.priceRepository = priceRepository;
        this.self = self;
    }

    /**
     * Resolve the cost (USD) of a single turn given its token counts. Returns
     * {@code 0.0} when no price is configured for the model (unknown or local).
     */
    public double computeCost(String vendorId, String modelName,
            long inputTokens, long outputTokens) {
        if (vendorId == null || modelName == null) {
            return 0.0;
        }
        Rates rates = self.rates(vendorId, modelName);
        double cost = (inputTokens / 1_000_000.0) * rates.inputPerMillion()
                + (outputTokens / 1_000_000.0) * rates.outputPerMillion();
        // Guard against negatives from a mis-entered price; never bill below 0.
        return cost > 0 ? cost : 0.0;
    }

    /**
     * Cached price-rate lookup. Always returns a value ({@link Rates#ZERO}
     * when no row exists) so {@code @Cacheable} can cache the miss too —
     * unknown models are by far the common case on local-only installs and we
     * don't want to hit the DB every turn for them.
     */
    @Cacheable(value = PRICE_CACHE, key = "#vendorId + ':' + #modelName")
    public Rates rates(String vendorId, String modelName) {
        return priceRepository.findByVendorIdAndModelName(vendorId, modelName)
                .map(p -> new Rates(p.getInputPricePerMillion(), p.getOutputPricePerMillion()))
                .orElse(Rates.ZERO);
    }

    public List<TurLLMPrice> findAll() {
        return priceRepository.findByOrderByVendorIdAscModelNameAsc();
    }

    /**
     * Create or update a price row. Upserts on the {@code (vendorId, modelName)}
     * natural key so the admin UI and the Liquibase seed converge instead of
     * fighting over the surrogate id.
     */
    @CacheEvict(value = PRICE_CACHE, allEntries = true)
    public TurLLMPrice save(TurLLMPrice price) {
        TurLLMPrice target = priceRepository
                .findByVendorIdAndModelName(price.getVendorId(), price.getModelName())
                .orElseGet(() -> {
                    TurLLMPrice fresh = new TurLLMPrice();
                    fresh.setVendorId(price.getVendorId());
                    fresh.setModelName(price.getModelName());
                    return fresh;
                });
        target.setInputPricePerMillion(price.getInputPricePerMillion());
        target.setOutputPricePerMillion(price.getOutputPricePerMillion());
        target.setCurrency(price.getCurrency() == null || price.getCurrency().isBlank()
                ? "USD" : price.getCurrency());
        target.setUpdatedAt(LocalDateTime.now());
        return priceRepository.save(target);
    }

    @CacheEvict(value = PRICE_CACHE, allEntries = true)
    public void delete(String id) {
        priceRepository.deleteById(id);
    }
}
