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
 * T258 / §XIV.2 — configuration for the multi-tenancy feature, bound under
 * {@code turing.tenancy.*}.
 *
 * <p>The single non-negotiable invariant of Block J: when {@link #enabled} is
 * {@code false} (the default), every request resolves the immutable
 * {@code DEFAULT} tenant and the discriminator collapses to a constant
 * equality, so single-tenant installs are byte-for-byte unchanged. This is the
 * project's standing "opt-in flag, default = legacy" rule applied to tenancy.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
public class TurTenancyProperty {

    /** Master switch for multi-tenancy. Default {@code false} = legacy single-tenant. */
    private boolean enabled = false;

    /**
     * T333 / §XIV.8 — when {@code true} (and {@link #enabled}), an authenticated
     * principal that resolves to no explicit tenant gets a <em>personal</em>
     * tenant (with an OWNER membership) auto-created on first request, and every
     * later request resolves to it via that membership. This is the
     * "one environment per user" SaaS posture used by Viglet Cloud.
     *
     * <p>Default {@code false} preserves the invite-based, many-users-per-tenant
     * model: a brand-new principal with no membership stays on {@code DEFAULT}
     * until explicitly added to a tenant.
     */
    private boolean autoProvision = false;

    /**
     * T334 / §XIV.8.1 — shared secret that marks a request as a <em>trusted
     * internal caller</em> (server-to-server). When this is set and a request
     * carries the matching {@code X-Turing-Internal-Token} header, the
     * {@code X-Turing-Tenant} header it stamps is honoured <strong>without</strong>
     * the per-user active-membership check — so an internal product (e.g. Dumont
     * calling {@code http://turing:2700} on behalf of an end user) can scope the
     * call to that user's tenant even though the cross-service principal holds no
     * membership in it. A suspended tenant is still blocked.
     *
     * <p>This is the alternative to forwarding the end user's bearer token (the
     * preferred path, which works unchanged via the JWT {@code tenant} claim).
     *
     * <p>Default empty/{@code null} = feature off: no header ever bypasses the
     * membership check, exactly as before. The token <strong>must</strong> only
     * be shared over a trusted internal network — any client that knows it can
     * assert any tenant.
     */
    private String internalToken;
}
