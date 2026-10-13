/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.dsl.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;

/**
 * A single declared field in the schema the NL→facet parser maps onto (T385 /
 * §XX.5). This is the lean, parser-facing projection of a {@code TurSNSiteFieldExt}
 * / manifest {@code TurSNJobAttributeSpec}: just the name, type and facet flag the
 * parser needs to decide between a term filter, a range, or full-text matching.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TurNLFacetField(
        String name,
        TurSEFieldType type,
        boolean facet,
        String description) {

    /**
     * T1085 — the projection of a live site field, the one mapping the NL filter
     * service, the catalog copilot and the eval all read the schema through.
     */
    public static TurNLFacetField of(TurSNSiteFieldExt ext) {
        TurSEFieldType type = ext.getType() != null ? ext.getType() : TurSEFieldType.TEXT;
        return new TurNLFacetField(ext.getName(), type, ext.getFacet() == 1, ext.getDescription());
    }

    /** True for numeric / currency types that admit range comparisons. */
    public boolean numeric() {
        return type == TurSEFieldType.INT
                || type == TurSEFieldType.LONG
                || type == TurSEFieldType.FLOAT
                || type == TurSEFieldType.DOUBLE
                || type == TurSEFieldType.CURRENCY;
    }
}
