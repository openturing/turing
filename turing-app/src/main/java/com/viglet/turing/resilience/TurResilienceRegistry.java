/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.resilience;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.viglet.turing.resilience.config.TurResilienceProfileResolver;
import com.viglet.turing.resilience.config.TurResilienceProperties;
import com.viglet.turing.resilience.config.TurResilienceProperties.Group;
import com.viglet.turing.resilience.config.TurResilienceProperties.Profile;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedTimeLimiterMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds and caches resilience primitives (CircuitBreaker, Retry, TimeLimiter)
 * per {@link Kind kind} and per provider/engine type.
 *
 * <p>Names are namespaced as {@code {kind}.{type}} (e.g. {@code llm.openai},
 * {@code search-engine.solr}) so Micrometer dashboards can filter by tag.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Slf4j
@Component
@EnableConfigurationProperties(TurResilienceProperties.class)
public class TurResilienceRegistry {

    public enum Kind {
        LLM("llm"),
        SEARCH_ENGINE("search-engine");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }

        public String prefix() {
            return prefix;
        }
    }

    private final TurResilienceProperties properties;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final ConcurrentMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Retry> retries = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TimeLimiter> timeLimiters = new ConcurrentHashMap<>();

    public TurResilienceRegistry(TurResilienceProperties properties,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.properties = properties;
        this.circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        this.retryRegistry = RetryRegistry.ofDefaults();
        this.timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
        if (meterRegistry != null) {
            TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry).bindTo(meterRegistry);
            TaggedRetryMetrics.ofRetryRegistry(retryRegistry).bindTo(meterRegistry);
            TaggedTimeLimiterMetrics.ofTimeLimiterRegistry(timeLimiterRegistry).bindTo(meterRegistry);
        }
    }

    public boolean isEnabled(Kind kind) {
        if (!properties.isEnabled()) {
            return false;
        }
        Group group = group(kind);
        return group != null && group.isEnabled();
    }

    public CircuitBreaker circuitBreaker(Kind kind, String type) {
        return circuitBreakers.computeIfAbsent(name(kind, type),
                key -> circuitBreakerRegistry.circuitBreaker(key, buildCircuitBreakerConfig(kind, type)));
    }

    public Retry retry(Kind kind, String type) {
        return retries.computeIfAbsent(name(kind, type),
                key -> retryRegistry.retry(key, buildRetryConfig(kind, type)));
    }

    public TimeLimiter timeLimiter(Kind kind, String type) {
        return timeLimiters.computeIfAbsent(name(kind, type),
                key -> timeLimiterRegistry.timeLimiter(key, buildTimeLimiterConfig(kind, type)));
    }

    /**
     * Provides a single shared scheduler for {@link TimeLimiter} executions.
     * Lazily owned by the caller (per-decorator); we just expose a small daemon pool
     * via {@link java.util.concurrent.Executors#newScheduledThreadPool(int)}-style facade.
     */
    public ScheduledExecutorService scheduler() {
        return SchedulerHolder.INSTANCE;
    }

    private CircuitBreakerConfig buildCircuitBreakerConfig(Kind kind, String type) {
        Profile profile = profile(kind, type);
        TurResilienceProperties.CircuitBreakerConfig cfg = profile.getCircuitBreaker();
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
        if (cfg != null) {
            if (cfg.getFailureRateThreshold() != null) {
                builder.failureRateThreshold(cfg.getFailureRateThreshold());
            }
            if (cfg.getSlowCallRateThreshold() != null) {
                builder.slowCallRateThreshold(cfg.getSlowCallRateThreshold());
            }
            if (cfg.getSlowCallDurationThreshold() != null) {
                builder.slowCallDurationThreshold(cfg.getSlowCallDurationThreshold());
            }
            if (cfg.getSlidingWindowSize() != null) {
                builder.slidingWindowSize(cfg.getSlidingWindowSize());
            }
            if (cfg.getMinimumNumberOfCalls() != null) {
                builder.minimumNumberOfCalls(cfg.getMinimumNumberOfCalls());
            }
            if (cfg.getWaitDurationInOpenState() != null) {
                builder.waitDurationInOpenState(cfg.getWaitDurationInOpenState());
            }
            if (cfg.getPermittedNumberOfCallsInHalfOpenState() != null) {
                builder.permittedNumberOfCallsInHalfOpenState(cfg.getPermittedNumberOfCallsInHalfOpenState());
            }
        }
        return builder.build();
    }

    private RetryConfig buildRetryConfig(Kind kind, String type) {
        Profile profile = profile(kind, type);
        TurResilienceProperties.RetryConfig cfg = profile.getRetry();
        RetryConfig.Builder<Object> builder = RetryConfig.custom();
        int maxAttempts = cfg != null && cfg.getMaxAttempts() != null ? cfg.getMaxAttempts() : 3;
        builder.maxAttempts(maxAttempts);
        Duration waitDuration = cfg != null && cfg.getWaitDuration() != null
                ? cfg.getWaitDuration()
                : Duration.ofMillis(500);
        Double multiplier = cfg != null ? cfg.getExponentialBackoffMultiplier() : null;
        if (multiplier != null && multiplier > 1.0) {
            builder.intervalFunction(io.github.resilience4j.core.IntervalFunction
                    .ofExponentialBackoff(waitDuration, multiplier));
        } else {
            builder.waitDuration(waitDuration);
        }
        List<Class<? extends Throwable>> retryOn = resolveRetryExceptions(
                cfg != null ? cfg.getRetryOnExceptions() : null);
        if (!retryOn.isEmpty()) {
            // Walk the cause chain — most providers wrap IOException in a RuntimeException,
            // and exact-type matching would miss the transient failure.
            builder.retryOnException(throwable -> matchesAnyCause(throwable, retryOn));
        }
        return builder.build();
    }

    private TimeLimiterConfig buildTimeLimiterConfig(Kind kind, String type) {
        Profile profile = profile(kind, type);
        TurResilienceProperties.TimeLimiterConfig cfg = profile.getTimeLimiter();
        TimeLimiterConfig.Builder builder = TimeLimiterConfig.custom();
        Duration timeout = cfg != null && cfg.getTimeoutDuration() != null
                ? cfg.getTimeoutDuration()
                : Duration.ofSeconds(60);
        builder.timeoutDuration(timeout);
        Boolean cancel = cfg != null ? cfg.getCancelRunningFuture() : null;
        if (cancel != null) {
            builder.cancelRunningFuture(cancel);
        }
        return builder.build();
    }

    private Profile profile(Kind kind, String type) {
        return TurResilienceProfileResolver.resolve(group(kind), type);
    }

    private Group group(Kind kind) {
        return switch (kind) {
            case LLM -> properties.getLlm();
            case SEARCH_ENGINE -> properties.getSearchEngine();
        };
    }

    private static String name(Kind kind, String type) {
        String safeType = type == null || type.isBlank() ? "default" : type.toLowerCase(Locale.ROOT);
        return kind.prefix() + "." + safeType;
    }

    private static boolean matchesAnyCause(Throwable throwable, List<Class<? extends Throwable>> classes) {
        Throwable current = throwable;
        // Bound the walk to avoid pathological cycles (some libraries set cause to self).
        for (int i = 0; current != null && i < 16; i++) {
            for (Class<? extends Throwable> cls : classes) {
                if (cls.isInstance(current)) {
                    return true;
                }
            }
            Throwable next = current.getCause();
            if (next == current) {
                return false;
            }
            current = next;
        }
        return false;
    }

    private List<Class<? extends Throwable>> resolveRetryExceptions(List<String> classNames) {
        if (classNames == null || classNames.isEmpty()) {
            return List.of(java.io.IOException.class);
        }
        List<Class<? extends Throwable>> resolved = new ArrayList<>();
        for (String className : classNames) {
            try {
                Class<?> loaded = Class.forName(className);
                if (Throwable.class.isAssignableFrom(loaded)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Throwable> typed = (Class<? extends Throwable>) loaded;
                    resolved.add(typed);
                } else {
                    log.warn("Configured retry-on class {} is not a Throwable; ignoring", className);
                }
            } catch (ClassNotFoundException e) {
                log.warn("Configured retry-on class {} not found on classpath; ignoring", className);
            }
        }
        return resolved.isEmpty() ? List.of(java.io.IOException.class) : resolved;
    }

    /** Lazy daemon scheduler used by all TimeLimiter executions. */
    private static final class SchedulerHolder {
        private static final ScheduledExecutorService INSTANCE = java.util.concurrent.Executors
                .newScheduledThreadPool(2, runnable -> {
                    Thread thread = new Thread(runnable, "tur-resilience-tl");
                    thread.setDaemon(true);
                    return thread;
                });
    }
}
