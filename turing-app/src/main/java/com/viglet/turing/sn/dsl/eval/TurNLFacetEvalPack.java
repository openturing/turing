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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A catalog NL→facet eval pack (T385 / §XX.5): a declared field schema plus a set
 * of prose queries with their golden structured outputs. Running the pack through
 * the {@link TurNLFacetParser} and scoring each case is how NL-parsing regressions
 * surface — the same idea the agent CI gate (Block&nbsp;K) uses for chat flows,
 * applied to the faceted-search parser that replaces a catalog's hand-written
 * {@code nlFilters}.
 *
 * <p>{@code fields} may be left empty, in which case {@link TurNLFacetEvalService}
 * resolves the schema from the live SN site named by {@code index}. Embedding the
 * schema in the pack makes it self-contained and DB-free for CI fixtures.
 *
 * @param name   pack label (shown in the report).
 * @param index  the SN site / index the prose queries target.
 * @param locale locale code passed to the parser (e.g. {@code pt_BR}); may be null.
 * @param fields the declared field schema; empty ⇒ resolve from the live site.
 * @param cases  the NL→facet cases to run.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TurNLFacetEvalPack(
        String name,
        String index,
        String locale,
        List<TurNLFacetField> fields,
        List<TurNLFacetEvalCase> cases) {

    public TurNLFacetEvalPack {
        fields = fields == null ? List.of() : fields;
        cases = cases == null ? List.of() : cases;
    }
}
