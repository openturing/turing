/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.sn.migration;

import java.util.List;

import com.viglet.turing.sn.migration.TurMigrationCompareMetrics.QueryComparison;

/**
 * Result of a shadow / dual-run comparison (T661 / §XXXVIII.5) — the aggregate
 * relevance-parity metrics plus the per-query breakdown.
 *
 * @param engine            the source engine compared against.
 * @param site              the Turing SN site.
 * @param queries           number of queries run on both sides.
 * @param rows              the top-N depth compared.
 * @param avgJaccard        mean set overlap (|∩|/|∪|) across queries (0..1).
 * @param avgSourceRecall   mean fraction of the source's top-N that Turing reproduced.
 * @param topRankMatches    queries where the #1 result is the same document.
 * @param zeroOverlapQueries queries where the two result sets share nothing.
 * @param perQuery          the per-query comparison detail.
 * @param warnings          non-fatal notes (a query that failed on one side, etc.).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurMigrationCompareResult(
        String engine,
        String site,
        int queries,
        int rows,
        double avgJaccard,
        double avgSourceRecall,
        int topRankMatches,
        int zeroOverlapQueries,
        List<QueryComparison> perQuery,
        List<String> warnings) {
}
