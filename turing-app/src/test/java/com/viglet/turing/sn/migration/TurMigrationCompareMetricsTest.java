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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.sn.migration.TurMigrationCompareMetrics.Aggregate;
import com.viglet.turing.sn.migration.TurMigrationCompareMetrics.QueryComparison;

/**
 * Unit tests for the shadow-comparison overlap metrics (T661).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurMigrationCompareMetricsTest {

    @Test
    void identicalResultSetsScorePerfectly() {
        QueryComparison c = TurMigrationCompareMetrics.compareQuery(
                "tv", List.of("a", "b", "c"), List.of("a", "b", "c"), 10);

        assertEquals(3, c.sharedCount());
        assertEquals(0, c.onlyInSource());
        assertEquals(0, c.onlyInTuring());
        assertEquals(1.0, c.jaccard());
        assertEquals(1.0, c.sourceRecall());
        assertTrue(c.topRankMatch());
    }

    @Test
    void partialOverlapComputesJaccardAndRecall() {
        QueryComparison c = TurMigrationCompareMetrics.compareQuery(
                "tv", List.of("a", "b", "c", "d"), List.of("a", "b", "x"), 10);

        // shared {a,b}=2, union {a,b,c,d,x}=5 → 0.4; recall 2/4 = 0.5
        assertEquals(2, c.sharedCount());
        assertEquals(0.4, c.jaccard());
        assertEquals(0.5, c.sourceRecall());
        assertTrue(c.topRankMatch(), "both start with 'a'");
    }

    @Test
    void topRankMismatchDetected() {
        QueryComparison c = TurMigrationCompareMetrics.compareQuery(
                "tv", List.of("a", "b"), List.of("b", "a"), 10);

        assertEquals(1.0, c.jaccard(), "same set");
        assertFalse(c.topRankMatch(), "different #1");
    }

    @Test
    void rowsTruncateBeforeComparing() {
        QueryComparison c = TurMigrationCompareMetrics.compareQuery(
                "tv", List.of("a", "b", "c"), List.of("a", "z"), 1);

        // truncated to 1: source {a}, turing {a}
        assertEquals(1, c.sourceCount());
        assertEquals(1, c.turingCount());
        assertEquals(1.0, c.jaccard());
    }

    @Test
    void disjointSetsScoreZeroOverlap() {
        QueryComparison c = TurMigrationCompareMetrics.compareQuery(
                "tv", List.of("a"), List.of("b"), 10);
        assertEquals(0, c.sharedCount());
        assertEquals(0.0, c.jaccard());
        assertEquals(0.0, c.sourceRecall());
    }

    @Test
    void aggregateAveragesAndCountsZeroOverlap() {
        List<QueryComparison> comps = List.of(
                TurMigrationCompareMetrics.compareQuery("q1", List.of("a", "b"), List.of("a", "b"), 10),
                TurMigrationCompareMetrics.compareQuery("q2", List.of("a"), List.of("z"), 10));

        Aggregate agg = TurMigrationCompareMetrics.aggregate(comps);
        assertEquals(0.5, agg.avgJaccard(), "(1.0 + 0.0)/2");
        assertEquals(1, agg.topRankMatches());
        assertEquals(1, agg.zeroOverlapQueries());
    }
}
