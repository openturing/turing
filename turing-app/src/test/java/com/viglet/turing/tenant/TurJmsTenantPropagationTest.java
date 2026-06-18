/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.properties.TurConfigProperties;

/**
 * Unit tests for {@link TurJmsTenantPropagation} (T272).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurJmsTenantPropagationTest {

    private TurTenantContext context;

    private TurJmsTenantPropagation propagation(boolean tenancyEnabled) {
        TurConfigProperties props = new TurConfigProperties();
        props.getTenancy().setEnabled(tenancyEnabled);
        context = new TurTenantContext(props);
        return new TurJmsTenantPropagation(context);
    }

    @AfterEach
    void clear() {
        if (context != null) {
            context.clear();
        }
    }

    @Test
    void headersCarryBoundTenantWhenEnabled() {
        TurJmsTenantPropagation propagation = propagation(true);
        var headers = context.runAs("acme", propagation::headers);
        assertThat(headers).containsEntry(TurJmsTenantPropagation.HEADER, "acme");
    }

    @Test
    void headersEmptyWhenOffOrDefault() {
        assertThat(propagation(false).headers()).isEmpty();
        assertThat(propagation(true).headers()).isEmpty(); // nothing bound -> DEFAULT
    }

    @Test
    void runForHeaderBindsTenantDuringProcessingThenClears() {
        TurJmsTenantPropagation propagation = propagation(true);
        StringBuilder seen = new StringBuilder();

        propagation.runForHeader("acme", () -> seen.append(context.getCurrentTenant()));

        assertThat(seen).hasToString("acme");
        assertThat(context.getCurrentTenant()).isNull(); // cleared after
    }

    @Test
    void runForHeaderClearsEvenWithNoHeader() {
        TurJmsTenantPropagation propagation = propagation(true);
        context.setCurrentTenant("leftover");

        propagation.runForHeader(null, () -> { /* no-op */ });

        assertThat(context.getCurrentTenant()).isNull(); // pooled thread must not leak
    }
}
