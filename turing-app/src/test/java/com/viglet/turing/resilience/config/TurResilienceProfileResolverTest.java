/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.resilience.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.resilience.config.TurResilienceProperties.CircuitBreakerConfig;
import com.viglet.turing.resilience.config.TurResilienceProperties.Group;
import com.viglet.turing.resilience.config.TurResilienceProperties.Profile;
import com.viglet.turing.resilience.config.TurResilienceProperties.RetryConfig;
import com.viglet.turing.resilience.config.TurResilienceProperties.TimeLimiterConfig;

class TurResilienceProfileResolverTest {

    @Test
    void resolve_returnsDefaultsWhenNoOverride() {
        Group group = group(profile(retry(3, Duration.ofSeconds(1)), null, null), Map.of());

        Profile resolved = TurResilienceProfileResolver.resolve(group, "openai");

        assertNotNull(resolved.getRetry());
        assertEquals(3, resolved.getRetry().getMaxAttempts());
        assertEquals(Duration.ofSeconds(1), resolved.getRetry().getWaitDuration());
    }

    @Test
    void resolve_overridesScalarFieldsWhilePreservingDefaults() {
        Profile defaults = profile(
                retry(3, Duration.ofSeconds(1)),
                circuitBreaker(50f, 20),
                timeLimiter(Duration.ofSeconds(60)));
        Profile override = new Profile();
        override.setTimeLimiter(timeLimiter(Duration.ofSeconds(300)));
        Group group = group(defaults, Map.of("ollama", override));

        Profile resolved = TurResilienceProfileResolver.resolve(group, "ollama");

        assertEquals(3, resolved.getRetry().getMaxAttempts());
        assertEquals(50f, resolved.getCircuitBreaker().getFailureRateThreshold());
        assertEquals(20, resolved.getCircuitBreaker().getSlidingWindowSize());
        assertEquals(Duration.ofSeconds(300), resolved.getTimeLimiter().getTimeoutDuration());
    }

    @Test
    void resolve_caseInsensitiveTypeKey() {
        Profile defaults = profile(retry(3, Duration.ofSeconds(1)), null, null);
        Profile override = new Profile();
        override.setRetry(retry(5, Duration.ofMillis(200)));
        Group group = group(defaults, Map.of("openai", override));

        Profile resolved = TurResilienceProfileResolver.resolve(group, "OpenAI");

        assertEquals(5, resolved.getRetry().getMaxAttempts());
    }

    @Test
    void resolve_listFieldOverrideReplacesEntirely() {
        RetryConfig baseRetry = new RetryConfig();
        baseRetry.setRetryOnExceptions(List.of("java.io.IOException"));
        Profile defaults = new Profile();
        defaults.setRetry(baseRetry);

        RetryConfig overrideRetry = new RetryConfig();
        overrideRetry.setRetryOnExceptions(List.of("java.lang.RuntimeException"));
        Profile override = new Profile();
        override.setRetry(overrideRetry);

        Group group = group(defaults, Map.of("openai", override));

        Profile resolved = TurResilienceProfileResolver.resolve(group, "openai");

        assertEquals(List.of("java.lang.RuntimeException"), resolved.getRetry().getRetryOnExceptions());
    }

    @Test
    void resolve_returnsCopyEvenWithoutType() {
        Profile defaults = profile(retry(3, Duration.ofSeconds(1)), null, null);
        Group group = group(defaults, Map.of());

        Profile resolved = TurResilienceProfileResolver.resolve(group, null);

        assertEquals(3, resolved.getRetry().getMaxAttempts());
        assertNull(resolved.getCircuitBreaker());
    }

    private static Profile profile(RetryConfig retry, CircuitBreakerConfig cb, TimeLimiterConfig tl) {
        Profile p = new Profile();
        p.setRetry(retry);
        p.setCircuitBreaker(cb);
        p.setTimeLimiter(tl);
        return p;
    }

    private static RetryConfig retry(int maxAttempts, Duration wait) {
        RetryConfig r = new RetryConfig();
        r.setMaxAttempts(maxAttempts);
        r.setWaitDuration(wait);
        return r;
    }

    private static CircuitBreakerConfig circuitBreaker(float failureRate, int slidingWindow) {
        CircuitBreakerConfig c = new CircuitBreakerConfig();
        c.setFailureRateThreshold(failureRate);
        c.setSlidingWindowSize(slidingWindow);
        return c;
    }

    private static TimeLimiterConfig timeLimiter(Duration timeout) {
        TimeLimiterConfig t = new TimeLimiterConfig();
        t.setTimeoutDuration(timeout);
        return t;
    }

    private static Group group(Profile defaults, Map<String, Profile> overrides) {
        Group g = new Group();
        g.setEnabled(true);
        g.setDefaults(defaults);
        g.setOverrides(new LinkedHashMap<>(overrides));
        return g;
    }
}
