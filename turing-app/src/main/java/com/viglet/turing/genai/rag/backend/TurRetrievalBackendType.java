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

import java.util.Locale;

/**
 * T520 / §XXVIII.16 — which retrieval backend serves RAG passages.
 *
 * <ul>
 *   <li>{@link #BUILT_IN} — the built-in vector / hybrid index (default). The
 *       retrieval core is unchanged; this is "no override".</li>
 *   <li>{@link #BEDROCK_KB} — an AWS Bedrock Knowledge Base via the
 *       {@code Retrieve} API, for tenants already standardized on a cloud RAG
 *       stack that want Turing as the agent/chat/eval layer on top.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurRetrievalBackendType {
    BUILT_IN,
    BEDROCK_KB;

    /** Lenient parse; unknown/blank/null falls back to {@link #BUILT_IN} (legacy). */
    public static TurRetrievalBackendType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return BUILT_IN;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BUILT_IN;
        }
    }
}
