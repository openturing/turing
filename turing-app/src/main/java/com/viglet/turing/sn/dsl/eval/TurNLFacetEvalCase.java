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

/**
 * One NL→facet eval case (T385 / §XX.5): a prose query plus the golden
 * structured output it must parse into.
 *
 * @param name   human-readable case label (shown in the report).
 * @param query  the natural-language prose, e.g. "online graduate programs under 20k".
 * @param expect the golden expected filters / ranges / sort.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TurNLFacetEvalCase(
        String name,
        String query,
        TurNLFacetExpectation expect) {
}
