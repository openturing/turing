/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.resilience.TurResilienceRegistry.Kind;
import com.viglet.turing.resilience.config.TurResilienceProperties;
import com.viglet.turing.resilience.config.TurResilienceProperties.Group;
import com.viglet.turing.resilience.config.TurResilienceProperties.Profile;
import com.viglet.turing.resilience.config.TurResilienceProperties.RetryConfig;

class TurResilienceExecutorTest {

    private TurResilienceProperties properties;
    private TurResilienceRegistry registry;
    private TurResilienceExecutor executor;

    @BeforeEach
    void setUp() {
        properties = new TurResilienceProperties();
        properties.setEnabled(true);

        Group llm = new Group();
        llm.setEnabled(true);
        Profile defaults = new Profile();
        RetryConfig retry = new RetryConfig();
        retry.setMaxAttempts(3);
        retry.setWaitDuration(java.time.Duration.ofMillis(10));
        retry.setRetryOnExceptions(java.util.List.of("java.io.IOException"));
        defaults.setRetry(retry);
        llm.setDefaults(defaults);
        properties.setLlm(llm);

        registry = new TurResilienceRegistry(properties, null);
        executor = new TurResilienceExecutor(registry);
    }

    @Test
    void execute_passThroughWhenGloballyDisabled() {
        properties.setEnabled(false);
        AtomicInteger calls = new AtomicInteger();

        Integer result = executor.execute(Kind.LLM, "openai", () -> {
            calls.incrementAndGet();
            return 42;
        });

        assertEquals(42, result);
        assertEquals(1, calls.get(), "Without resilience the supplier runs exactly once");
    }

    @Test
    void execute_retriesOnRetriableException() {
        AtomicInteger attempts = new AtomicInteger();

        Integer result = executor.execute(Kind.LLM, "openai", () -> {
            int n = attempts.incrementAndGet();
            if (n < 3) {
                throw new RuntimeException(new IOException("transient"));
            }
            return 7;
        });

        assertEquals(7, result);
        assertEquals(3, attempts.get(), "Retry should fire on IOException up to maxAttempts");
    }

    @Test
    void execute_doesNotRetryOnNonRetriableException() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(RuntimeException.class, () -> executor.execute(Kind.LLM, "openai", () -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("permanent");
        }));

        assertEquals(1, attempts.get(),
                "IllegalArgumentException is not in retry-on list, so the call must not be retried");
    }
}
