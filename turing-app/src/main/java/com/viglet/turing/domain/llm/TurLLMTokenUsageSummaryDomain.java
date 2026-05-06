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
package com.viglet.turing.domain.llm;

import java.time.LocalDate;

/**
 * Aggregated projection of {@link TurLLMTokenUsageDomain} rows — either
 * grouped per {@code (day, instance, model)} for the daily-usage feed or
 * per {@code (instance, model)} for the monthly-summary feed. Free of JPA
 * annotations; the adapter constructs instances directly from the
 * repository's {@code Object[]} JPQL projection rows.
 *
 * <p>Two factory methods correspond to the two query shapes so the
 * adapter routes daily vs. monthly results unambiguously: {@code day} is
 * non-null on daily rows and {@code null} on monthly rows.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurLLMTokenUsageSummaryDomain(
        LocalDate day,
        String instanceId,
        String instanceTitle,
        String vendorId,
        String modelName,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        long requestCount) {

    /** Daily-usage projection (day + instance/model + summed counts). */
    public static TurLLMTokenUsageSummaryDomain daily(LocalDate day, String instanceId,
            String instanceTitle, String vendorId, String modelName,
            long inputTokens, long outputTokens, long totalTokens, long requestCount) {
        return new TurLLMTokenUsageSummaryDomain(day, instanceId, instanceTitle, vendorId,
                modelName, inputTokens, outputTokens, totalTokens, requestCount);
    }

    /** Monthly-summary projection (no day; instance/model + summed counts). */
    public static TurLLMTokenUsageSummaryDomain monthly(String instanceId,
            String instanceTitle, String vendorId, String modelName,
            long inputTokens, long outputTokens, long totalTokens, long requestCount) {
        return new TurLLMTokenUsageSummaryDomain(null, instanceId, instanceTitle, vendorId,
                modelName, inputTokens, outputTokens, totalTokens, requestCount);
    }
}
