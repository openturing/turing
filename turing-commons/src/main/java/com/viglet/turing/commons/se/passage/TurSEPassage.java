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
package com.viglet.turing.commons.se.passage;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * T1044 / §XX.21 — one sub-document passage a search engine matched, returned
 * alongside the document rather than in place of it.
 *
 * <p>A passage is <em>not</em> a highlight and not a field value. A highlight is
 * the engine marking up a field it already returned — Turing's Solr path merges
 * one back over the field's own value, which is why highlighting needs no type of
 * its own. A passage is a separately-embedded fragment the engine chose, carrying
 * its own relevance score, and the document's fields say nothing about which
 * fragment matched.
 *
 * <p>It exists because an engine Turing does not own may be the only thing that
 * knows how a document was chunked. On a federated index — Adobe AEM Content AI
 * is the first ({@code chunks[]} with {@code chunkData.text}) — Turing cannot
 * re-chunk the content, so the passage the engine returns is the only
 * sub-document grounding available anywhere in the system. Without it an agent
 * grounds on a whole page.
 *
 * @param id    the engine's identifier for the chunk
 * @param score the engine's relevance score for this passage
 * @param text  the passage text, or {@code null} when the engine returned a
 *              scored chunk reference without its content
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TurSEPassage(String id, Double score, String text) implements Serializable {

    /** Whether this passage carries text a caller can ground on. */
    public boolean hasText() {
        return text != null && !text.isBlank();
    }
}
