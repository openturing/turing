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
package com.viglet.turing.sn.synonym;

import java.util.ArrayList;
import java.util.List;

/**
 * T667 / §XXXIX (Block AP) — pure, deterministic clustering of query-log terms
 * by embedding proximity, the first stage of AI-assisted synonym mining. A
 * greedy nearest-centroid pass groups terms whose vectors exceed a cosine
 * similarity threshold; each group with two or more terms becomes a
 * <em>candidate</em> synonym set proposed for human review (never auto-applied,
 * per the grounding invariant). Kept free of Spring/embeddings so the grouping
 * is fully unit-testable with hand-authored vectors.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurSynonymClusterer {

    /** A term paired with its embedding vector. */
    public record TurSynonymCandidate(String term, float[] vector) {
    }

    private TurSynonymClusterer() {
    }

    /**
     * Groups candidates by embedding proximity.
     *
     * @param candidates     the terms + vectors to cluster (input order is stable)
     * @param threshold      minimum cosine similarity to join a cluster (0..1)
     * @param maxClusterSize cap on a cluster's size (0/negative = unbounded)
     * @return clusters of ≥2 terms, each a proposed synonym set
     */
    public static List<List<String>> cluster(List<TurSynonymCandidate> candidates,
            double threshold, int maxClusterSize) {
        List<List<String>> terms = new ArrayList<>();
        List<float[]> centroids = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        if (candidates == null) {
            return List.of();
        }

        for (TurSynonymCandidate candidate : candidates) {
            if (candidate == null || candidate.term() == null || candidate.term().isBlank()
                    || candidate.vector() == null || candidate.vector().length == 0) {
                continue;
            }
            String term = candidate.term().trim();
            int best = bestCluster(term, candidate.vector(), terms, centroids, sizes, threshold,
                    maxClusterSize);
            if (best >= 0) {
                terms.get(best).add(term);
                mergeCentroid(centroids.get(best), sizes.get(best), candidate.vector());
                sizes.set(best, sizes.get(best) + 1);
            } else {
                List<String> group = new ArrayList<>();
                group.add(term);
                terms.add(group);
                centroids.add(candidate.vector().clone());
                sizes.add(1);
            }
        }

        List<List<String>> out = new ArrayList<>();
        for (List<String> group : terms) {
            if (group.size() >= 2) {
                out.add(group);
            }
        }
        return out;
    }

    private static int bestCluster(String term, float[] vector, List<List<String>> terms,
            List<float[]> centroids, List<Integer> sizes, double threshold, int maxClusterSize) {
        int best = -1;
        double bestSim = threshold;
        for (int i = 0; i < centroids.size(); i++) {
            if (maxClusterSize > 0 && sizes.get(i) >= maxClusterSize) {
                continue;
            }
            if (terms.get(i).stream().anyMatch(t -> t.equalsIgnoreCase(term))) {
                continue; // don't add a term already present in this cluster
            }
            double sim = cosine(vector, centroids.get(i));
            if (sim >= bestSim) {
                bestSim = sim;
                best = i;
            }
        }
        return best;
    }

    /** Incrementally folds a new vector into a running mean centroid. */
    private static void mergeCentroid(float[] centroid, int size, float[] vector) {
        int n = Math.min(centroid.length, vector.length);
        for (int i = 0; i < n; i++) {
            centroid[i] = (centroid[i] * size + vector[i]) / (size + 1);
        }
    }

    static double cosine(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < n; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
