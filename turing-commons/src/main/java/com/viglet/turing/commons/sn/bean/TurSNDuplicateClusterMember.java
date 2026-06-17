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

/**
 * One document inside a {@link TurSNDuplicateCluster} — the same real-world
 * entity as the others in its cluster, kept with its provenance so a catalog can
 * collapse the duplicates into one result while still listing every source it
 * came from.
 *
 * @param id         the document id
 * @param title      the document title (may be null)
 * @param type       the document type (may be null)
 * @param url        the document url (may be null)
 * @param source     provenance — the originating source/app of this copy
 *                   (the {@code source_apps} field), or null when not indexed
 * @param similarity similarity to the seed document, or null for the seed itself
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNDuplicateClusterMember(
        String id,
        String title,
        String type,
        String url,
        String source,
        Double similarity) implements Serializable {
}
