/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.citation;

/**
 * T152 / §X.7.a — one per-sentence citation returned by Anthropic Citations,
 * resolved back onto the source passage it points at.
 *
 * <p>Anthropic returns each citation as a location into the ordered list of
 * {@code document} blocks sent in the request ({@code documentIndex}). The
 * Messages service resolves that index against the turn's
 * {@link TurCitationDocument} list and copies {@code sourceId} / {@code url} so
 * the streamed citation is self-contained — the citation-aware chat UI (T154)
 * underlines {@code citedText} in the answer and deep-links to {@code url}.
 *
 * <p>Two location shapes are normalised here:
 * <ul>
 *   <li>{@code locationType = "char"} — plain-text source: {@code startIndex} /
 *       {@code endIndex} are character offsets into the passage.</li>
 *   <li>{@code locationType = "page"} — PDF source: {@code startIndex} /
 *       {@code endIndex} are 1-based page numbers (rendered as a {@code (p. N)}
 *       chip).</li>
 * </ul>
 *
 * @param documentIndex  zero-based index into the turn's document-block list
 * @param sourceId       stable id of the cited source (resolved from the list)
 * @param documentTitle  title Claude echoed back (falls back to the source title)
 * @param url            deep link to the source (may be {@code null})
 * @param citedText      the exact span of the source Claude grounded the claim on
 * @param startIndex     start char offset (char) or start page (page), nullable
 * @param endIndex       end char offset (char) or end page (page), nullable
 * @param locationType   {@code "char"} or {@code "page"}
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurChatCitation(
        int documentIndex,
        String sourceId,
        String documentTitle,
        String url,
        String citedText,
        Integer startIndex,
        Integer endIndex,
        String locationType) {
}
