/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.ranking;

/**
 * A vector neighbor paired with its similarity score (cosine — higher is more
 * similar). Returned by
 * {@link TurSNHybridRankingService#findScoredNeighbors}; the score is what lets
 * the T390 duplicate-clustering path apply a high-similarity threshold to tell
 * "near-identical" apart from merely "related".
 *
 * @param id    the neighbor document id
 * @param score the similarity score against the seed, or {@code null} when the
 *              vector store does not expose one (a conservative null means the
 *              neighbor is treated as below any positive threshold)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNScoredNeighbor(String id, Double score) {
}
