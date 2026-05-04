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
package com.viglet.turing.domain.sn;

import java.time.Instant;

/**
 * Aggregated projection of {@link TurSNSiteMetricAccessDomain} rows —
 * either {@code (term, accessDate)} for the latest-searches feed or
 * {@code (term, total, numFound)} for the top-terms feed. Free of JPA
 * annotations; the adapter constructs instances directly from the
 * repository's projection result class.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurSNSiteMetricAccessTermDomain(
        String term,
        Instant accessDate,
        long total,
        double numFound) {

    /** Latest-searches projection (term + most recent access date). */
    public static TurSNSiteMetricAccessTermDomain latest(String term, Instant accessDate) {
        return new TurSNSiteMetricAccessTermDomain(term, accessDate, 0L, 0.0);
    }

    /** Top-terms projection (term + total accesses + average numFound). */
    public static TurSNSiteMetricAccessTermDomain aggregated(String term, long total,
            double numFound) {
        return new TurSNSiteMetricAccessTermDomain(term, null, total, numFound);
    }
}
