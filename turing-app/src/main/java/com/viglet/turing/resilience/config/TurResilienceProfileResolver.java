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
package com.viglet.turing.resilience.config;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.viglet.turing.resilience.config.TurResilienceProperties.CircuitBreakerConfig;
import com.viglet.turing.resilience.config.TurResilienceProperties.Group;
import com.viglet.turing.resilience.config.TurResilienceProperties.Profile;
import com.viglet.turing.resilience.config.TurResilienceProperties.RetryConfig;
import com.viglet.turing.resilience.config.TurResilienceProperties.TimeLimiterConfig;

/**
 * Merges per-type overrides on top of group defaults to produce a fully-populated
 * effective profile. Null fields in the override fall back to the defaults.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public final class TurResilienceProfileResolver {

    private TurResilienceProfileResolver() {}

    public static Profile resolve(Group group, String typeKey) {
        Profile defaults = group.getDefaults() != null ? group.getDefaults() : new Profile();
        if (typeKey == null) {
            return copy(defaults);
        }
        Map<String, Profile> overrides = group.getOverrides();
        Profile override = overrides == null ? null : overrides.get(typeKey.toLowerCase(Locale.ROOT));
        if (override == null) {
            return copy(defaults);
        }
        Profile merged = new Profile();
        merged.setRetry(mergeRetry(defaults.getRetry(), override.getRetry()));
        merged.setCircuitBreaker(mergeCircuitBreaker(defaults.getCircuitBreaker(), override.getCircuitBreaker()));
        merged.setTimeLimiter(mergeTimeLimiter(defaults.getTimeLimiter(), override.getTimeLimiter()));
        return merged;
    }

    private static Profile copy(Profile source) {
        Profile copy = new Profile();
        copy.setRetry(mergeRetry(source.getRetry(), null));
        copy.setCircuitBreaker(mergeCircuitBreaker(source.getCircuitBreaker(), null));
        copy.setTimeLimiter(mergeTimeLimiter(source.getTimeLimiter(), null));
        return copy;
    }

    private static RetryConfig mergeRetry(RetryConfig base, RetryConfig override) {
        if (base == null && override == null) {
            return null;
        }
        RetryConfig merged = new RetryConfig();
        merged.setMaxAttempts(pick(getValue(base, RetryConfig::getMaxAttempts),
                getValue(override, RetryConfig::getMaxAttempts)));
        merged.setWaitDuration(pick(getValue(base, RetryConfig::getWaitDuration),
                getValue(override, RetryConfig::getWaitDuration)));
        merged.setExponentialBackoffMultiplier(pick(getValue(base, RetryConfig::getExponentialBackoffMultiplier),
                getValue(override, RetryConfig::getExponentialBackoffMultiplier)));
        List<String> baseExceptions = getValue(base, RetryConfig::getRetryOnExceptions);
        List<String> overrideExceptions = getValue(override, RetryConfig::getRetryOnExceptions);
        merged.setRetryOnExceptions(overrideExceptions != null ? overrideExceptions : baseExceptions);
        return merged;
    }

    private static CircuitBreakerConfig mergeCircuitBreaker(CircuitBreakerConfig base, CircuitBreakerConfig override) {
        if (base == null && override == null) {
            return null;
        }
        CircuitBreakerConfig merged = new CircuitBreakerConfig();
        merged.setFailureRateThreshold(pick(getValue(base, CircuitBreakerConfig::getFailureRateThreshold),
                getValue(override, CircuitBreakerConfig::getFailureRateThreshold)));
        merged.setSlowCallRateThreshold(pick(getValue(base, CircuitBreakerConfig::getSlowCallRateThreshold),
                getValue(override, CircuitBreakerConfig::getSlowCallRateThreshold)));
        merged.setSlowCallDurationThreshold(pick(getValue(base, CircuitBreakerConfig::getSlowCallDurationThreshold),
                getValue(override, CircuitBreakerConfig::getSlowCallDurationThreshold)));
        merged.setSlidingWindowSize(pick(getValue(base, CircuitBreakerConfig::getSlidingWindowSize),
                getValue(override, CircuitBreakerConfig::getSlidingWindowSize)));
        merged.setMinimumNumberOfCalls(pick(getValue(base, CircuitBreakerConfig::getMinimumNumberOfCalls),
                getValue(override, CircuitBreakerConfig::getMinimumNumberOfCalls)));
        merged.setWaitDurationInOpenState(pick(getValue(base, CircuitBreakerConfig::getWaitDurationInOpenState),
                getValue(override, CircuitBreakerConfig::getWaitDurationInOpenState)));
        merged.setPermittedNumberOfCallsInHalfOpenState(
                pick(getValue(base, CircuitBreakerConfig::getPermittedNumberOfCallsInHalfOpenState),
                        getValue(override, CircuitBreakerConfig::getPermittedNumberOfCallsInHalfOpenState)));
        return merged;
    }

    private static TimeLimiterConfig mergeTimeLimiter(TimeLimiterConfig base, TimeLimiterConfig override) {
        if (base == null && override == null) {
            return null;
        }
        TimeLimiterConfig merged = new TimeLimiterConfig();
        Duration baseTimeout = getValue(base, TimeLimiterConfig::getTimeoutDuration);
        Duration overrideTimeout = getValue(override, TimeLimiterConfig::getTimeoutDuration);
        merged.setTimeoutDuration(pick(baseTimeout, overrideTimeout));
        merged.setCancelRunningFuture(pick(getValue(base, TimeLimiterConfig::getCancelRunningFuture),
                getValue(override, TimeLimiterConfig::getCancelRunningFuture)));
        return merged;
    }

    private static <T, R> R getValue(T source, java.util.function.Function<T, R> getter) {
        return source == null ? null : getter.apply(source);
    }

    private static <T> T pick(T base, T override) {
        return override != null ? override : base;
    }
}
