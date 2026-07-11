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

package com.viglet.turing.sn.synonym;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.sn.synonym.TurSynonymClusterer.TurSynonymCandidate;

/**
 * Unit tests for the pure embedding-proximity synonym clustering (T667).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSynonymClustererTest {

    private static TurSynonymCandidate c(String term, float... v) {
        return new TurSynonymCandidate(term, v);
    }

    @Test
    void groupsNearbyTermsAndSplitsDistantOnes() {
        // Two tight groups on orthogonal axes.
        List<List<String>> clusters = TurSynonymClusterer.cluster(List.of(
                c("tv", 1f, 0f),
                c("television", 0.99f, 0.02f),
                c("telly", 0.98f, 0.05f),
                c("laptop", 0f, 1f),
                c("notebook", 0.02f, 0.99f)), 0.9, 0);
        assertEquals(2, clusters.size());
        assertTrue(clusters.stream().anyMatch(g -> g.containsAll(List.of("tv", "television", "telly"))));
        assertTrue(clusters.stream().anyMatch(g -> g.containsAll(List.of("laptop", "notebook"))));
    }

    @Test
    void singletonsAreDropped() {
        // "unique" has no neighbour above threshold -> not a synonym set.
        List<List<String>> clusters = TurSynonymClusterer.cluster(List.of(
                c("tv", 1f, 0f),
                c("television", 0.99f, 0.02f),
                c("unique", -1f, 0f)), 0.9, 0);
        assertEquals(1, clusters.size());
        assertEquals(List.of("tv", "television"), clusters.get(0));
    }

    @Test
    void respectsMaxClusterSize() {
        List<List<String>> clusters = TurSynonymClusterer.cluster(List.of(
                c("a", 1f, 0f), c("b", 1f, 0f), c("c", 1f, 0f), c("d", 1f, 0f)), 0.9, 2);
        // cap of 2 → the first two land together; the rest form a second pair.
        assertTrue(clusters.stream().allMatch(g -> g.size() <= 2));
        assertEquals(4, clusters.stream().mapToInt(List::size).sum());
    }

    @Test
    void skipsBlankAndEmptyVectors() {
        List<List<String>> clusters = TurSynonymClusterer.cluster(List.of(
                c("tv", 1f, 0f),
                c("  ", 1f, 0f),
                new TurSynonymCandidate("television", new float[0]),
                c("telly", 0.98f, 0.05f)), 0.9, 0);
        assertEquals(1, clusters.size());
        assertEquals(List.of("tv", "telly"), clusters.get(0));
    }
}
