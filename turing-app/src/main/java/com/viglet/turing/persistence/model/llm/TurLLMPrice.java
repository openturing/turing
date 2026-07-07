/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.llm;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * T289 / §XVI.1 — admin-editable per-model price table backing the Block L
 * cost &amp; token governance layer. One row per {@code (vendorId, modelName)}
 * pair holds the price (in USD) of a million prompt tokens and a million
 * completion tokens; {@link com.viglet.turing.service.llm.price.TurLLMPriceService}
 * multiplies those rates by the per-turn token counts captured from the Spring
 * AI {@code ChatResponse} usage metadata to produce the per-call USD cost
 * stamped onto {@link TurLLMTokenUsage}.
 *
 * <p>Provider-agnostic by construction: a local / embedded model (price 0)
 * records {@code costUsd = 0}, preserving the "$0 fully local" story. Defaults
 * for well-known models are seeded by Liquibase; the table is fully editable
 * from the admin UI so customers can plug their negotiated rates.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "tur_llm_price",
        uniqueConstraints = @UniqueConstraint(name = "uk_llm_price_vendor_model",
                columnNames = {"vendor_id", "model_name"}))
public class TurLLMPrice implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** Vendor / provider slug (e.g. {@code openai}, {@code anthropic}). */
    @Column(name = "vendor_id", nullable = false, length = 20)
    private String vendorId;

    /** Model identifier as reported by the instance (e.g. {@code gpt-4o-mini}). */
    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    /** USD charged per 1,000,000 prompt (input) tokens. */
    @Column(name = "input_price_per_million", nullable = false)
    private double inputPricePerMillion;

    /** USD charged per 1,000,000 completion (output) tokens. */
    @Column(name = "output_price_per_million", nullable = false)
    private double outputPricePerMillion;

    /** ISO-4217 currency the rates are quoted in. Always {@code USD} today. */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
