/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import java.io.Serializable;
import java.util.List;

/**
 * T487 / §XXVIII.2 — immutable read-model for an SN site's enabled field
 * configuration, as consumed by the Semantic Navigation {@code dsl_get_mappings}
 * tool.
 *
 * <p>It is a flat, session-free projection of {@code TurSNSite} +
 * {@code TurSNSiteFieldExt} — the field {@code name}, indexing {@code type}, and
 * the {@code facet}/{@code multiValued} flags plus an optional description. It
 * carries <em>no</em> JPA entities or lazy proxies, so it is safe to memoize in
 * a service-level cache and read outside a transaction (Block AC) and is
 * {@link Serializable} for a clustered Hazelcast {@code IMap}.
 *
 * @param siteName the canonical SN site name (used as the mappings JSON root key)
 * @param fields   the enabled fields, in repository order
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNSiteFieldConfigModel(String siteName, List<FieldInfo> fields)
        implements Serializable {

    public TurSNSiteFieldConfigModel {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    /**
     * One enabled field's mapping projection.
     *
     * @param name        the field name (mappings property key)
     * @param type        the lowercased indexing type (e.g. {@code text}, {@code date})
     * @param facet       whether the field is faceted
     * @param multiValued whether the field is multi-valued
     * @param description an optional human description, or {@code null}
     */
    public record FieldInfo(String name, String type, boolean facet, boolean multiValued,
            String description) implements Serializable {
    }
}
