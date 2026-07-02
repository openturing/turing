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
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T183 / §X.14.c — one row per (day × vendor × workspace × model × service tier)
 * of <strong>vendor-reported</strong> token consumption + USD cost, imported
 * nightly from a provider's Admin/Usage API (Anthropic first, via
 * {@code TurAnthropicUsageImporter}).
 *
 * <p>This is the authoritative billing number — distinct from the live
 * {@code llm_token_usage} estimate Turing computes per turn (Block L / T289).
 * Where both exist for a period, the vendor figure here is the source of truth
 * and the live number is the early-warning estimate (the T184 AI-Spend widget
 * reconciles them).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "tur_ai_usage_daily")
public class TurAiUsageDaily implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** The UTC day this usage was reported for. */
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "vendor_id", nullable = false, length = 20)
    private String vendorId;

    /** Vendor workspace / project id the usage was attributed to (nullable). */
    @Column(name = "workspace_id", length = 255)
    private String workspaceId;

    @Column(name = "model_name", length = 100)
    private String modelName;

    /** Vendor service tier (e.g. {@code standard}, {@code batch}, {@code priority}); nullable. */
    @Column(name = "service_tier", length = 40)
    private String serviceTier;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    /** Vendor-reported USD cost for this slice. {@code 0.0} when only usage (no cost) was available. */
    @Column(name = "cost_usd", nullable = false)
    private double costUsd;

    /**
     * Owning tenant for cost attribution (mirrors {@code llm_token_usage}). Plain
     * column so platform billing rolls up across tenants; {@code DEFAULT} for
     * single-tenant installs. Today the vendor Admin API has no Turing-tenant
     * notion, so the importer stamps the current tenant of the import run.
     */
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
