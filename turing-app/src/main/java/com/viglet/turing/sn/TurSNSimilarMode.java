/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn;

/**
 * T384 / §XX.4 — retrieval strategy for the public "similar documents" endpoint.
 *
 * <ul>
 *   <li>{@link #MLT} — lexical MoreLikeThis over the live search-engine index
 *       (works on every site, no embeddings required).</li>
 *   <li>{@link #VECTOR} — semantic vector-neighbor retrieval over the per-site
 *       {@code sn_<siteId>} collection (requires a {@code HYBRID_RRF} site with a
 *       default embedding model + store; degrades to {@link #MLT} otherwise).</li>
 * </ul>
 *
 * <p>When the request leaves the mode unspecified the service picks
 * {@link #VECTOR} for hybrid-ranking sites and {@link #MLT} otherwise.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurSNSimilarMode {
    MLT,
    VECTOR
}
