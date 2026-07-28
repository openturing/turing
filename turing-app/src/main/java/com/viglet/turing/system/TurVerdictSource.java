/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.system;

/**
 * T829 / §LXI.5 (Block BN) — <em>how</em> a schema-bound LLM verdict was obtained.
 *
 * <p>The point is to make a degradation legible instead of silent. Before T829 a
 * caller could only say "no LLM was used", which conflated two very different
 * situations: nothing is configured, versus the model answered and its reply could
 * not be read. The second one is a quality bug worth chasing; the first is just
 * configuration.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurVerdictSource {

    /** The provider enforced the schema (OpenAI {@code json_schema} / Anthropic tool). */
    PROVIDER_SCHEMA,

    /**
     * A plain chat call answered and the JSON had to be scraped out of the reply —
     * the legacy path, kept as the last fallback for providers with no structured
     * surface.
     */
    PROMPT_JSON,

    /** Served from the LLM cache, so how it was originally produced is not known. */
    CACHE,

    /** No usable reply at all — no LLM configured, or the call failed. */
    UNAVAILABLE,

    /**
     * The model replied but the verdict could not be decoded — the degradation that
     * used to look identical to {@link #UNAVAILABLE} in the UI.
     */
    UNREADABLE
}
