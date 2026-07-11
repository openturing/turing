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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure, deterministic result-set overlap metrics for the shadow / dual-run
 * comparison (T661 / §XXXVIII.5).
 *
 * <p>Given the source engine's and Turing's top-N ids for the same query, it
 * measures how close the two result sets are — so a team can quantify relevance
 * parity before cutover. No engine calls happen here; this is the scoring half and
 * is fully unit-testable.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurMigrationCompareMetrics {

    private TurMigrationCompareMetrics() {
        // static-only
    }

    /** Per-query overlap between the source and Turing top-N result sets. */
    public record QueryComparison(
            String query,
            int sourceCount,
            int turingCount,
            int sharedCount,
            int onlyInSource,
            int onlyInTuring,
            double jaccard,
            double sourceRecall,
            boolean topRankMatch) {
    }

    /**
     * Compares the two ordered id lists (each truncated to {@code rows}) for one query.
     *
     * <ul>
     *   <li><b>jaccard</b> — |∩| / |∪| of the two top-N sets (1.0 when both empty).</li>
     *   <li><b>sourceRecall</b> — fraction of the source's top-N that Turing also
     *       returned (1.0 when the source returned nothing).</li>
     *   <li><b>topRankMatch</b> — the #1 result is the same document.</li>
     * </ul>
     */
    public static QueryComparison compareQuery(String query, List<String> sourceIds,
            List<String> turingIds, int rows) {
        List<String> source = truncate(sourceIds, rows);
        List<String> turing = truncate(turingIds, rows);
        Set<String> sourceSet = new LinkedHashSet<>(source);
        Set<String> turingSet = new LinkedHashSet<>(turing);

        Set<String> shared = new LinkedHashSet<>(sourceSet);
        shared.retainAll(turingSet);
        Set<String> union = new LinkedHashSet<>(sourceSet);
        union.addAll(turingSet);

        double jaccard = union.isEmpty() ? 1.0 : (double) shared.size() / union.size();
        double sourceRecall = sourceSet.isEmpty() ? 1.0 : (double) shared.size() / sourceSet.size();
        boolean topRankMatch = !source.isEmpty() && !turing.isEmpty()
                && source.get(0).equals(turing.get(0));

        return new QueryComparison(query, sourceSet.size(), turingSet.size(), shared.size(),
                sourceSet.size() - shared.size(), turingSet.size() - shared.size(),
                round(jaccard), round(sourceRecall), topRankMatch);
    }

    /** Aggregate averages over a set of per-query comparisons. */
    public record Aggregate(double avgJaccard, double avgSourceRecall, int topRankMatches,
            int zeroOverlapQueries) {
    }

    public static Aggregate aggregate(List<QueryComparison> comparisons) {
        if (comparisons == null || comparisons.isEmpty()) {
            return new Aggregate(0.0, 0.0, 0, 0);
        }
        double jaccard = 0.0;
        double recall = 0.0;
        int topMatches = 0;
        int zeroOverlap = 0;
        for (QueryComparison c : comparisons) {
            jaccard += c.jaccard();
            recall += c.sourceRecall();
            if (c.topRankMatch()) {
                topMatches++;
            }
            if (c.sharedCount() == 0) {
                zeroOverlap++;
            }
        }
        int n = comparisons.size();
        return new Aggregate(round(jaccard / n), round(recall / n), topMatches, zeroOverlap);
    }

    private static List<String> truncate(List<String> ids, int rows) {
        if (ids == null) {
            return List.of();
        }
        if (rows <= 0 || ids.size() <= rows) {
            return new ArrayList<>(ids);
        }
        return new ArrayList<>(ids.subList(0, rows));
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
