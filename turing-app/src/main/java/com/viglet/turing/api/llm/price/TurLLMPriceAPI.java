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

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public TurLLMPriceAPI(TurLLMPriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping
    public List<TurLLMPrice> list() {
        return priceService.findAll();
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
