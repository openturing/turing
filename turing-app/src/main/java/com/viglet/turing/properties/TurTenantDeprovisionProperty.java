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

import lombok.Getter;
import lombok.Setter;

/**
 * T336 / §XIV.8.3 — configuration for the <em>lifecycle close</em> on identity
 * deletion, bound under {@code turing.tenancy.deprovision.*}.
 *
 * <p>When a Viglet Cloud user is deleted or disabled in Keycloak, the personal
 * tenant the T333 auto-provisioner minted for them is left orphaned. This
 * feature runs a periodic reconciliation against the identity provider and
 * <em>suspends</em> (and, opt-in, later <em>tears down</em> via the T281
 * {@code TurTenantTeardownService}) any tenant whose every member has vanished
 * from the IdP.
 *
 * <p>Default {@code enabled=false} keeps the sweep dormant — the standing
 * "opt-in flag, default = legacy" rule. The sweep also no-ops whenever the
 * identity provider cannot be queried (no Keycloak service-account credentials
 * configured), so it can never act on a guess.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
public class TurTenantDeprovisionProperty {

    /** Master switch for the reconciliation sweep. Default {@code false} = dormant. */
    private boolean enabled = false;

    /**
     * Cron expression for the sweep. Default: daily at 03:25. Cluster-wide-once
     * via ShedLock, so multi-node deploys never run it twice.
     */
    private String cron = "0 25 3 * * *";

    /**
     * What to do with an orphaned personal tenant. Default {@link
     * TurTenantDeprovisionMode#SUSPEND_ONLY} only blocks auth; the destructive
     * {@link TurTenantDeprovisionMode#SUSPEND_THEN_DELETE} must be chosen
     * explicitly.
     */
    private TurTenantDeprovisionMode mode = TurTenantDeprovisionMode.SUSPEND_ONLY;

    /**
     * In {@link TurTenantDeprovisionMode#SUSPEND_THEN_DELETE}, the minimum number
     * of days a tenant must stay suspended-and-orphaned before it is hard-deleted.
     * The grace window is anchored on {@code TurTenant.suspendedAt}, so deletion
     * always requires a separate later sweep — a transient IdP blip is recoverable.
     */
    private int deleteAfterDays = 30;

    /**
     * Keycloak service-account (client-credentials) client id used to query the
     * Admin REST API <em>in the background</em>, where there is no logged-in
     * user token to delegate to. The client must hold a {@code realm-management}
     * role that can read users (e.g. {@code view-users}). Blank disables the
     * sweep (it can't verify identities, so it does nothing).
     */
    private String keycloakClientId;

    /** Secret for {@link #keycloakClientId}. Blank disables the sweep. */
    private String keycloakClientSecret;
}
