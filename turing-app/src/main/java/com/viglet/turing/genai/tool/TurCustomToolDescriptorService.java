/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Builds the descriptor consumed by the Custom Tool Groovy editor to drive
 * auto-complete and signature hints. The descriptor lists every binding the
 * runtime injects into a Custom Tool script — {@code http} / {@code code} /
 * {@code slots} / {@code turingSearch} — alongside method signatures, common
 * named-argument keys, return types, and one-line descriptions.
 *
 * <p>Static catalogue by design. The bindings are wired in
 * {@link TurCustomToolCallbackService#executeGroovy} and aren't dynamically
 * extensible, so an in-code catalogue is both honest and trivially testable.
 * If/when the binding set grows, this is the single place to keep in sync.
 *
 * <p>Consumed by {@code TurCustomToolAPI#descriptor()} →
 * {@code GET /api/custom-tool/descriptor} → CodeMirror autocomplete extension
 * on the Custom Tool admin page. The shape is deliberately kept editor-agnostic
 * (no CodeMirror-specific fields) so a future Monaco or LSP path can consume
 * the same payload.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurCustomToolDescriptorService {

    /**
     * Returns the full editor descriptor — list of helpers and the always-present
     * global bindings (e.g. {@code args}). Cheap call (returns the same
     * structurally-immutable record graph every time), but not cached because
     * the cost is negligible and a fresh instance avoids accidental client
     * mutation of a shared structure.
     */
    public ToolEditorDescriptor get() {
        return new ToolEditorDescriptor(buildHelpers(), buildGlobals());
    }

    private List<HelperDescriptor> buildHelpers() {
        return List.of(
                httpHelper(),
                slotsHelper(),
                turingSearchHelper(),
                codeHelper(),
                agentHelper());
    }

    private List<GlobalDescriptor> buildGlobals() {
        return List.of(
                new GlobalDescriptor("args", "Map<String, Object>",
                        "Parsed LLM-supplied arguments. Each declared parameter is also bound as a top-level "
                                + "variable with the same name (e.g. `args.query` ↔ `query`)."));
    }

    private HelperDescriptor httpHelper() {
        return new HelperDescriptor("http", "TurCustomToolHttpHelper",
                "HTTP client returning parsed JSON. Chainable auth helpers (`bearer`, `basic`) and "
                        + "reserved `__bearer_token` / `__basic_user` + `__basic_password` slot prefix for "
                        + "auto-attached `Authorization` headers (never echoed in logs).",
                List.of(
                        method("getJson", "getJson(url: String, headers: Map<String,String> = null) -> Object",
                                "GET `url`, parse the JSON body. Caller-supplied `Authorization` "
                                        + "header overrides chain/slot auto-auth."),
                        method("postJson",
                                "postJson(url: String, body: Object, headers: Map<String,String> = null) -> Object",
                                "POST `body` (any JSON-serializable value, usually a Map) and parse "
                                        + "the JSON response."),
                        method("bearer", "bearer(token: String) -> TurCustomToolHttpHelper",
                                "Returns a NEW helper that pins `Authorization: Bearer <token>` on "
                                        + "every subsequent request. Immutable — original helper is "
                                        + "unchanged. `null`/blank token clears explicit auth."),
                        method("basic", "basic(user: String, password: String) -> TurCustomToolHttpHelper",
                                "Returns a NEW helper that pins `Authorization: Basic "
                                        + "<base64(user:password)>`. Immutable; same null-clearing semantics "
                                        + "as `bearer()`.")));
    }

    private HelperDescriptor slotsHelper() {
        return new HelperDescriptor("slots", "TurCustomToolSlotHelper",
                "Conversation-scoped read/write access to chat-flow variables. The React portal reads "
                        + "the same slots via `useTuringSlot`/`useTuringSlots`. Reserved `__`-prefixed slots "
                        + "(`__bearer_token`, `__basic_user`, `__basic_password`) drive `http.*` auto-auth.",
                List.of(
                        method("set", "set(name: String, value: String) -> void",
                                "Persist `name = value` on the current conversation's chat-flow state. "
                                        + "SSE-emits the merged slot map so subscribers refresh immediately. "
                                        + "Null/blank `name` is a no-op."),
                        method("get", "get(name: String) -> String",
                                "Read a slot value across every state attached to this conversation. "
                                        + "Returns `null` when unset."),
                        method("all", "all() -> Map<String, String>",
                                "Returns the full slot map for the current conversation (merged across "
                                        + "states; freshly allocated, safe to mutate locally).")));
    }

    private HelperDescriptor turingSearchHelper() {
        return new HelperDescriptor("turingSearch", "TurCustomToolSearchHelper",
                "High-level wrappers over the ANN vector store (`ann`) and the SN site search (`sn`). "
                        + "Both methods take a Groovy named-arg Map and return a `List<Map<String,Object>>` — "
                        + "metadata flattened to top-level + the reserved keys `id`, `score`, `content`.",
                List.of(
                        method("ann",
                                "ann(site: String, query: String, locale: String = null, "
                                        + "topK: Integer = 8, filters: Map = null, "
                                        + "templateName: String = null, dedupBy: Object = null) "
                                        + "-> List<Map<String,Object>>",
                                "Semantic similarity search with chunk-to-document dedup. Over-fetches "
                                        + "chunks under the hood and collapses to unique source documents — "
                                        + "`topK` then means unique documents. `dedupBy = \"none\"` disables "
                                        + "dedup for highlight-grade chunk results."),
                        method("sn",
                                "sn(site: String, query: String = \"*\", locale: String = null, "
                                        + "rows: Integer = 10, page: Integer = 1, sort: String = null, "
                                        + "fq: Map = null, fl: String = null, "
                                        + "templateName: String = null) -> List<Map<String,Object>>",
                                "Faceted SN site search via `TurSNSearchProcess.search` — full-text "
                                        + "behavior with `fq` filter queries, `fl` field projection, pagination "
                                        + "and sort. Aliases: `q`/`query`, `rows`/`pageSize`.")));
    }

    private HelperDescriptor codeHelper() {
        return new HelperDescriptor("code", "TurCustomToolCodeHelper",
                "Sandboxed Python execution via the platform's Code Interpreter. Two overloads — string "
                        + "script or named-arg `script:` + `inputs:` Map (auto-encoded as a global "
                        + "`INPUTS` dict in the Python prelude).",
                List.of(
                        method("executePython", "executePython(pythonCode: String) -> String",
                                "Run a single Python script in the sandbox with the union of global + "
                                        + "agent `pythonRequirements` available. 30s timeout. Returns the "
                                        + "markdown output from the sandbox."),
                        method("executePython",
                                "executePython(namedArgs: Map [script: String, inputs: Object]) -> String",
                                "Named-arg overload. JSON+base64 encodes `inputs` and prepends a prelude "
                                        + "exposing it as the Python global `INPUTS` — eliminates the brittle "
                                        + "heredoc-concat pattern.")));
    }

    private HelperDescriptor agentHelper() {
        return new HelperDescriptor("agent", "TurCustomToolAgentHelper",
                "Cross-agent delegation — invokes another Turing AI Agent in an isolated child conversation "
                        + "and returns its final assistant text. Recursion capped at 3 levels. Failure modes "
                        + "are returned as bracketed sentinels (e.g. `[agent.invoke timeout after 30000ms]`) — "
                        + "never thrown, so the orchestrator LLM sees a stable failure shape.",
                List.of(
                        method("invoke", "invoke(agentId: String, message: String) -> String",
                                "Send `message` to the target agent's first enabled LLM in a fresh child "
                                        + "conversation. Default timeout 30s. The parent conversation id is "
                                        + "carried in the child's first user turn for trace reconstruction."),
                        method("invoke",
                                "invoke(agentId: String, message: String, "
                                        + "opts: Map [timeout: Long, llmInstanceId: String]) -> String",
                                "Options form. `timeout` in ms (default 30000, capped at 300000). "
                                        + "`llmInstanceId` overrides the auto-picked LLM — must be one of the "
                                        + "target agent's configured LLMs.")));
    }

    private static MethodDescriptor method(String name, String signature, String description) {
        return new MethodDescriptor(name, signature, description);
    }

    /**
     * Top-level payload returned by {@code GET /api/custom-tool/descriptor}.
     *
     * @param helpers ordered list of binding helpers; order is the
     *                presentation order for the editor's auto-complete UI
     *                (most-frequently-used first).
     * @param globals always-present non-helper bindings (e.g. {@code args}).
     */
    public record ToolEditorDescriptor(List<HelperDescriptor> helpers, List<GlobalDescriptor> globals) {
    }

    /**
     * A single helper exposed under a Groovy binding name.
     *
     * @param name        the binding name as seen in a Groovy script
     *                    (e.g. {@code "http"} → {@code http.getJson(...)}).
     * @param className   underlying Java class — for hover docs / "jump to
     *                    source" affordances in a future LSP path.
     * @param description one-line summary shown in the autocomplete popover
     *                    when the binding name itself is hovered.
     * @param methods     callable methods on the helper.
     */
    public record HelperDescriptor(String name, String className, String description,
            List<MethodDescriptor> methods) {
    }

    /**
     * One callable method on a helper. Signature is hand-written rather than
     * reflected so we can express named-arg conventions and default values
     * the Groovy script-side actually uses (and which reflection can't see).
     */
    public record MethodDescriptor(String name, String signature, String description) {
    }

    /**
     * A non-helper binding (e.g. {@code args}). Surfaced separately because
     * it has no methods of its own — it's just a typed value the script
     * can reference directly.
     */
    public record GlobalDescriptor(String name, String type, String description) {
    }
}
