/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.commons.sn.bean;

import java.io.Serializable;
import java.util.List;

/**
 * T390 / §XX.10 — the cluster of near-identical documents around a seed: the
 * same real-world entity (a course listed by five portals) collapsed into one
 * result, with every member kept so the catalog can show the duplicates' shared
 * provenance instead of repeating the entity five times.
 *
 * <p>The {@code canonicalId} is chosen deterministically (the lexicographically
 * smallest member id) so that querying with any member of the cluster yields the
 * same representative — clustering is content-based only and never prefers one
 * source commercially (the Block&nbsp;R objective-ranking invariant).
 *
 * <p>Surfaced two ways: standalone via {@code GET /api/sn/{site}/search/duplicates},
 * and inline on each search result ({@code TurSNSiteSearchDocumentBean.duplicateCluster})
 * for sites with MoreLikeThis enabled on a hybrid ranking mode.
 *
 * @param canonicalId the chosen representative document id (never null when
 *                   {@code members} is non-empty)
 * @param size        the number of documents in the cluster (= {@code members.size()})
 * @param duplicate   whether the seed has at least one near-identical sibling
 *                   ({@code size > 1})
 * @param members     every document in the cluster, the seed first, then by
 *                   similarity to the seed descending
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNDuplicateCluster(
        String canonicalId,
        int size,
        boolean duplicate,
        List<TurSNDuplicateClusterMember> members) implements Serializable {
}
