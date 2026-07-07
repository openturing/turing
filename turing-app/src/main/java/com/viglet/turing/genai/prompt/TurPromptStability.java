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
package com.viglet.turing.genai.prompt;

/**
 * Block AL / §XXXV — how stable a {@link TurPromptSegment} is across the turns
 * of one conversation.
 *
 * <ul>
 *   <li>{@link #STABLE} — the segment's text does not change turn-to-turn for a
 *       given agent/persona (persona-static block, agent base prompt, MCP server
 *       instructions, capability guidance, skill catalog, tool schemas). These
 *       are the segments that a provider prefix cache (Anthropic
 *       {@code cache_control} / Gemini {@code cachedContents} — T502 / T499) can
 *       cache once and reuse.</li>
 *   <li>{@link #PER_TURN} — the segment depends on the live turn (few-shot
 *       examples retrieved by the user message, the active flow node addendum,
 *       "already collected" state, RAG context, history). Placing these
 *       <em>after</em> the STABLE segments (T614) keeps the cacheable prefix
 *       contiguous.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurPromptStability {
    /** Constant across the turns of a conversation — belongs in the cacheable prefix. */
    STABLE,
    /** Varies per turn — must sit after the STABLE prefix so the prefix stays cacheable. */
    PER_TURN
}
