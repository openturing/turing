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
package com.viglet.turing.genai.nativeapi.capability;

/**
 * T432 / §X.18 — well-known abstract <em>function</em> keys. A capability's
 * {@code function} is what it <em>does</em> independent of who runs it; two
 * capabilities sharing a function are alternative implementations of the same
 * thing and become mutually exclusive in the agent picker (T434).
 *
 * <p>Only the genuinely cross-source overlaps need a shared constant here.
 * Capabilities with no alternative implementation (a Turing-only tool like
 * {@code finance}) simply use their own group id as the function and never
 * collide — so this list stays small on purpose. Functions are free-form
 * strings (not an enum) so future capabilities can introduce new functions
 * without touching this class.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurCapabilityFunctions {

    private TurCapabilityFunctions() {
    }

    /** Search the public web. OpenAI {@code web_search} ↔ Anthropic {@code web_search}. */
    public static final String WEB_SEARCH = "web-search";

    /** Fetch a specific URL's content. Anthropic {@code web_fetch}. */
    public static final String WEB_FETCH = "web-fetch";

    /** Crawl/fetch web content via Turing's own crawler tool. Kept distinct from
     *  {@link #WEB_SEARCH}/{@link #WEB_FETCH} — crawling a known source is not the
     *  same capability as a web search, so it is not forced into that mutex. */
    public static final String WEB_CRAWL = "web-crawl";

    /** Search hosted/document vector stores. OpenAI {@code file_search}. */
    public static final String FILE_SEARCH = "file-search";

    /** Execute code in a sandbox. Turing code-interpreter ↔ OpenAI
     *  {@code code_interpreter} ↔ Anthropic {@code code_execution}. */
    public static final String CODE_EXEC = "code-exec";

    /** Generate images inline. OpenAI {@code image_generation}. */
    public static final String IMAGE_GEN = "image-gen";

    /** Call a remote MCP server. OpenAI remote {@code mcp} (Anthropic connector via T144). */
    public static final String MCP = "mcp";

    /** Drive a screen/browser via the multi-turn screenshot loop. OpenAI ↔ Anthropic
     *  {@code computer_use} — both are {@code ownsTurn} capabilities. */
    public static final String COMPUTER_USE = "computer-use";

    /** Edit files via the Anthropic {@code text_editor} client tool (against the workspace). */
    public static final String TEXT_EDITOR = "text-editor";

    /** Run shell commands via the Anthropic {@code bash} client tool. */
    public static final String BASH = "bash";

    /** Persist/recall files across turns via the Anthropic {@code memory} client
     *  tool (T163), executed against the per-conversation workspace. */
    public static final String MEMORY = "memory";

    /** Real-time bidirectional speech (speech-in / speech-out). OpenAI Realtime
     *  ({@code gpt-realtime}); an {@code ownsTurn} capability — a voice session
     *  takes over the whole conversation transport. */
    public static final String VOICE = "voice";
}
