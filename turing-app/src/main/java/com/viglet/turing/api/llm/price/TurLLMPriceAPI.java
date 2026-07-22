/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.llm.price;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.provider.llm.TurCatalogPlans;
import com.viglet.turing.genai.provider.llm.TurLlmModelCatalog;
import com.viglet.turing.persistence.model.llm.TurLLMPrice;
import com.viglet.turing.service.llm.price.TurLLMPriceService;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T290 / §XVI.2 (Block L) — admin CRUD for the per-model price table that backs
 * the cost dashboard. {@code save} upserts on the {@code (vendorId, modelName)}
 * natural key and evicts the price cache so an edited rate applies on the next
 * chat turn.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/v2/llm/price")
@Tag(name = "LLM Price Table", description = "Block L — per-model price table")
public class TurLLMPriceAPI {

    private final TurLLMPriceService priceService;
    private final TurLlmModelCatalog modelCatalog;

    public TurLLMPriceAPI(TurLLMPriceService priceService, TurLlmModelCatalog modelCatalog) {
        this.priceService = priceService;
        this.modelCatalog = modelCatalog;
    }

    @GetMapping
    public List<TurLLMPrice> list() {
        return priceService.findAll();
    }

    /**
     * T778 — per-vendor official "verify at vendor" pricing-page links, keyed by
     * lower-cased vendor slug, from the public model catalog's {@code providers()}
     * registry. Empty until the catalog has refreshed. The cost UI links each
     * price row (especially indicative catalog rows) to its vendor page so an
     * operator can confirm the rate.
     */
    @GetMapping("/provider-links")
    public Map<String, String> providerLinks() {
        return modelCatalog.providerPricingUrls();
    }

    /**
     * T789 — indicative consumer subscription plans per vendor (Claude Pro,
     * ChatGPT Plus…) from the public catalog's {@code plans()} registry, for the
     * admin-help pricing reference. Reference only — always verify at the vendor.
     */
    @GetMapping("/consumer-plans")
    public Map<String, List<TurCatalogPlans.TurCatalogPlan>> consumerPlans() {
        return modelCatalog.consumerPlans().byVendor();
    }

    @PostMapping
    public TurLLMPrice save(@RequestBody TurLLMPriceRequest request) {
        TurLLMPrice price = new TurLLMPrice();
        price.setVendorId(request.vendorId());
        price.setModelName(request.modelName());
        price.setInputPricePerMillion(request.inputPricePerMillion());
        price.setOutputPricePerMillion(request.outputPricePerMillion());
        price.setCurrency(request.currency());
        return priceService.save(price);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        priceService.delete(id);
    }
}
