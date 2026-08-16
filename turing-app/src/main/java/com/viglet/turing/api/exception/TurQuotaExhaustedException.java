/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.exception;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;

/**
 * T959 / §XIV.10.4 — a plan limit was reached, and the refusal carries the exit.
 *
 * <p>The shared {@code VigletQuotaService} answers over-limit work with a bare
 * status (402 on agents and sites, 429 on monthly tokens), which is correct as a
 * gate and useless as a product surface: the operator sees a request fail and
 * cannot tell whether the install is broken, the provider is down, or the free
 * credit simply ran out.
 *
 * <p>This exception is the enriched form Turing rethrows instead, so the console
 * can render what ran out, when it resets, and the two ways forward:
 *
 * <ul>
 *   <li>{@code resource} — which limit was reached ({@code agents}, {@code sites},
 *       {@code monthlyTokens}).</li>
 *   <li>{@code used} / {@code limit} — the counts behind the refusal.</li>
 *   <li>{@code resetsAt} — when the window rolls over, or {@code null} for a
 *       standing limit (a resource count does not reset by waiting).</li>
 *   <li>{@code upgradeUrl} — the <b>signed</b> upgrade target, or {@code null}
 *       when this install cannot sign one. Never unsigned: an unsigned link
 *       crossing from the managed service into a self-hosted console is an open
 *       redirect with a friendly name.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurQuotaExhaustedException extends TurApiException {

    private final String resource;
    private final long used;
    private final long limit;
    private final transient OffsetDateTime resetsAt;
    private final String upgradeUrl;

    public TurQuotaExhaustedException(HttpStatus status, String resource, long used, long limit,
            OffsetDateTime resetsAt, String upgradeUrl, String message) {
        super(status, TurErrorTypes.QUOTA_EXHAUSTED, message);
        this.resource = resource;
        this.used = used;
        this.limit = limit;
        this.resetsAt = resetsAt;
        this.upgradeUrl = upgradeUrl;
    }

    public String getResource() {
        return resource;
    }

    public long getUsed() {
        return used;
    }

    public long getLimit() {
        return limit;
    }

    /** When the window rolls over, or {@code null} for a limit that never resets on its own. */
    public OffsetDateTime getResetsAt() {
        return resetsAt;
    }

    /** The signed upgrade target, or {@code null} when none could be signed. */
    public String getUpgradeUrl() {
        return upgradeUrl;
    }
}
