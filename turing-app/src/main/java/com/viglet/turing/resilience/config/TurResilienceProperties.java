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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Resilience configuration for outbound calls (LLM providers, search engines).
 *
 * <p>Two layers: <b>defaults</b> applies to every dispatched call of the group;
 * per-provider/per-engine entries override individual fields. Null fields are
 * inherited from defaults.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "turing.resilience")
public class TurResilienceProperties {

    /** Global kill switch. When false, no resilience is applied to any group. */
    private boolean enabled = true;

    private Group llm = new Group();

    private Group searchEngine = new Group();

    @Getter
    @Setter
    public static class Group {
        private boolean enabled = true;
        private Profile defaults = new Profile();
        /** Per-type override (e.g. "openai", "ollama" for llm; "solr", "lucene" for search-engine). */
        private Map<String, Profile> overrides = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    public static class Profile {
        private RetryConfig retry;
        private CircuitBreakerConfig circuitBreaker;
        private TimeLimiterConfig timeLimiter;
    }

    @Getter
    @Setter
    public static class RetryConfig {
        private Integer maxAttempts;
        private Duration waitDuration;
        private Double exponentialBackoffMultiplier;
        private List<String> retryOnExceptions;
    }

    @Getter
    @Setter
    public static class CircuitBreakerConfig {
        private Float failureRateThreshold;
        private Float slowCallRateThreshold;
        private Duration slowCallDurationThreshold;
        private Integer slidingWindowSize;
        private Integer minimumNumberOfCalls;
        private Duration waitDurationInOpenState;
        private Integer permittedNumberOfCallsInHalfOpenState;
    }

    @Getter
    @Setter
    public static class TimeLimiterConfig {
        private Duration timeoutDuration;
        private Boolean cancelRunningFuture;
    }
}
