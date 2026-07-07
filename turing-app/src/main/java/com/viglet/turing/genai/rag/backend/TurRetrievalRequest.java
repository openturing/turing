/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.backend;

/**
 * T520 / §XXVIII.16 — the inputs a {@link TurRetrievalBackend} needs to fetch
 * passages: the natural-language {@code query}, how many to return ({@code topK}),
 * and the optional BCP-47 query {@code locale} (a managed backend may route to a
 * per-locale index). Provider-agnostic so the built-in and managed backends share
 * one contract.
 *
 * @param query  the natural-language query (never blank when invoked)
 * @param topK   max passages to return (clamped by the backend)
 * @param locale BCP-47 language tag, or {@code null} when unknown
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurRetrievalRequest(String query, int topK, String locale) {

    public TurRetrievalRequest(String query, int topK) {
        this(query, topK, null);
    }
}
