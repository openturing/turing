/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.properties;

/**
 * T336 / §XIV.8.3 — what the identity-reconciliation sweep does to a personal
 * tenant whose owner(s) have disappeared from the identity provider.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurTenantDeprovisionMode {

    /**
     * Only ever <em>suspend</em> an orphaned tenant (block auth, retain data).
     * The hard delete is left to a human / a separate runbook. This is the
     * default — the conservative posture.
     */
    SUSPEND_ONLY,

    /**
     * Suspend an orphaned tenant first, then <em>hard-delete</em> it (purge
     * storage/Lucene/caches + drop the registry rows via the T281 teardown
     * service) once it has stayed suspended-and-orphaned for at least
     * {@code deleteAfterDays}. Always a two-sweep, time-gated process so a
     * transient IdP outage can never trigger an immediate destructive purge.
     */
    SUSPEND_THEN_DELETE
}
