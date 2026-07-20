/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.gateway;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T741 / §XLIX — a <b>virtual key</b> for the Governed LLM Gateway (Block AZ):
 * the {@code sk-turing-...} bearer credential a client presents at {@code /v1/*}.
 * It is the "give a dev a key scoped to gpt-4o with a monthly budget" primitive.
 *
 * <p>Secret handling mirrors {@code TurLLMInstance}: the raw key is shown to the
 * operator exactly once at creation and never again. At rest we keep a SHA-256
 * {@code keyHash} for O(1), tenant-agnostic authentication lookup, plus the raw
 * key encrypted via {@code TurSecretCryptoService} ({@code secretEncrypted}) so
 * an admin can reveal/copy it later; {@code keyPrefix} ({@code sk-turing-••••})
 * is the safe display form.</p>
 *
 * <p>{@code tenantId} is a <b>plain column</b>, deliberately NOT
 * {@code @TenantId}: the bearer filter authenticates a presented key <i>before</i>
 * the tenant is resolved (the key is what establishes the tenant), so the lookup
 * must be global — it then binds the tenant from the key. Same rationale as
 * {@code TurLLMInstance} / {@code TurLLMTokenUsage}.</p>
 *
 * <p>Per-key budget / rate-limit columns are added by T742 ({@code addColumn}).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "tur_gateway_key")
public class TurGatewayKey implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 40)
    private String id;

    /** Human label shown in the admin ("Marketing dev key"). */
    @Column(nullable = false, length = 150)
    private String name;

    /** Safe display form of the key, e.g. {@code sk-turing-a1b2c3••••}. */
    @Column(name = "key_prefix", nullable = false, length = 40)
    private String keyPrefix;

    /** SHA-256 hex of the raw key — the unique, tenant-agnostic authentication lookup. */
    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    /** The raw key encrypted at rest (reveal/rotate); never serialised out. */
    @Column(name = "secret_encrypted")
    @JsonIgnore
    private String secretEncrypted;

    /**
     * Comma-separated allow-list of model ids/names this key may call
     * ({@code null}/blank = any model). Enforced by the gateway service.
     */
    @Column(name = "allowed_models", length = 2000)
    private String allowedModels;

    /** Owning tenant (plain column — see class Javadoc); {@code null} = platform-global. */
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    /** {@code 1} = enabled, {@code 0} = revoked/disabled. */
    @Column(nullable = false)
    private int enabled = 1;

    /** Optional expiry; a key past this instant no longer authenticates. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * T742 / §XLIX — soft month-to-date USD budget. When spend reaches it, the
     * gateway auto-downgrades to {@link #budgetDowngradeLlmId} (if set) instead of
     * the requested model. {@code null}/{@code <=0} = no soft budget.
     */
    @Column(name = "monthly_budget_usd")
    private Double monthlyBudgetUsd;

    /** T742 — LLM instance id to fall back to once the soft budget is hit. */
    @Column(name = "budget_downgrade_llm_id", length = 40)
    private String budgetDowngradeLlmId;

    /**
     * T742 — hard month-to-date USD kill-switch. When spend reaches it the call
     * is refused (429, no upstream LLM call). {@code null}/{@code <=0} = off.
     */
    @Column(name = "hard_monthly_cap_usd")
    private Double hardMonthlyCapUsd;

    /** T742 — max requests per minute for this key. {@code null}/{@code <=0} = unlimited. */
    @Column(name = "rate_limit_per_minute")
    private Integer rateLimitPerMinute;

    @Column(name = "creation_date")
    private LocalDateTime creationDate;

    @Column(name = "modification_date")
    private LocalDateTime modificationDate;

    /**
     * Whether this key currently authenticates: enabled and not past its expiry.
     */
    @JsonIgnore
    public boolean isUsable() {
        return enabled == 1
                && (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
    }
}
