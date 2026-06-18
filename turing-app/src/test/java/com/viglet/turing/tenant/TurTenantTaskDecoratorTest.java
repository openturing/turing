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

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.properties.TurConfigProperties;

/**
 * Unit tests for {@link TurTenantTaskDecorator} and
 * {@link TurTenantThreadLocalAccessor} (T274).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurTenantTaskDecoratorTest {

    private final TurTenantContext context = newContext();

    private TurTenantContext newContext() {
        TurConfigProperties props = new TurConfigProperties();
        props.getTenancy().setEnabled(true);
        return new TurTenantContext(props);
    }

    @AfterEach
    void clear() {
        context.clear();
    }

    @Test
    void decoratorRestoresCapturedTenantOnWorkerThenClears() throws Exception {
        context.setCurrentTenant("acme");
        AtomicReference<String> seenOnWorker = new AtomicReference<>();

        Runnable decorated = new TurTenantTaskDecorator(context)
                .decorate(() -> seenOnWorker.set(context.getCurrentTenant()));

        // Run on a different thread to simulate the async hop.
        Thread worker = new Thread(decorated);
        worker.start();
        worker.join();

        assertThat(seenOnWorker.get()).isEqualTo("acme");
    }

    @Test
    void accessorReadsWritesAndClearsTheTenant() {
        TurTenantThreadLocalAccessor accessor = new TurTenantThreadLocalAccessor(context);

        assertThat(accessor.key()).isEqualTo(TurTenantThreadLocalAccessor.CONTEXT_KEY);
        accessor.setValue("acme");
        assertThat(accessor.getValue()).isEqualTo("acme");
        accessor.setValue();
        assertThat(accessor.getValue()).isNull();
    }
}
