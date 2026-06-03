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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Convenience helper exposed to Custom Tool Groovy scripts under the binding
 * variable name {@code code}. Wraps {@link TurCodeInterpreterToolService} so
 * customer-authored scripts can hand off heavy work (PDF rendering, charting,
 * data transformation) to the platform's sandboxed Python runtime without
 * re-implementing process management.
 *
 * <p>Each instance is constructed per Custom Tool invocation by
 * {@link TurCustomToolCallbackService}, carrying:
 * <ul>
 *   <li>{@code agentRequirements} — the active agent's
 *       {@code pythonRequirements} (unioned with the global setting before
 *       pip install).</li>
 *   <li>{@code agentId} + {@code conversationId} — published by
 *       {@link com.viglet.turing.genai.TurAgentChatExecutor} into the tool
 *       context; used by the Code Interpreter to land the session dir
 *       under {@code tenants/{agentId}/{conversationId}/...} for path-level
 *       isolation between customers / visitors.</li>
 * </ul>
 *
 * <p><b>Note on isolation:</b> the tenant subtree is OPERATIONAL isolation
 * (cleanup scoping, ownership-aware auth on the file API later) — NOT a
 * runtime security boundary. A Python script with malicious intent could
 * still {@code open("../../other-tenant/file")} since the subprocess
 * runs as the JVM user. Proper runtime isolation needs containerization
 * (Docker / gVisor / Firecracker) — separate roadmap.
 *
 * <p>Usage in Groovy — the markdown form (LLM echoes the result verbatim):
 * <pre>{@code
 *   def result = code.executePython('''
 *       from reportlab.platypus import SimpleDocTemplate
 *       # ... build a PDF
 *       print('done')
 *   ''')
 * }</pre>
 *
 * <p>Or the <b>structured</b> form (T83) — no regex parsing of the markdown.
 * {@code executePythonStructured} returns a
 * {@link TurCodeInterpreterToolService.TurCodeInterpreterResult} whose fields
 * ({@code stdout}, {@code files}, {@code exitCode}, {@code timedOut},
 * {@code durationMs}) are read directly:
 * <pre>{@code
 *   def r = code.executePythonStructured('''
 *       # ... build a PDF named proposta.pdf
 *   ''')
 *   if (r.success() && r.files()) {
 *       slots.set("proposta_pdf_url", r.files()[0].url())
 *   }
 * }</pre>
 *
 * <p>{@code code.executePythonJson(...)} returns the same data as a compact
 * JSON string ({@code {session_id, success, exit_code, timed_out, duration_ms,
 * stdout, stderr, files:[{name,url,image,size_bytes}]}}) for callers that
 * prefer {@code new groovy.json.JsonSlurper().parseText(...)} or want to feed
 * a webhook payload directly.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public class TurCustomToolCodeHelper {

    /**
     * Shared Jackson ObjectMapper for {@link #executePython(Map)}'s
     * inputs-to-JSON step. Configuration is the default — no pretty-print,
     * no special date handling, so the generated JSON is compact and
     * stable. Created once per helper instance to amortize the small but
     * non-zero ObjectMapper construction cost across multiple tool calls.
     */
    private static final ObjectMapper INPUTS_JSON = JsonMapper.builder().build();

    private final TurCodeInterpreterToolService codeInterpreter;
    private final String agentRequirements;
    private final String agentId;
    private final String conversationId;

    public TurCustomToolCodeHelper(TurCodeInterpreterToolService codeInterpreter) {
        this(codeInterpreter, null, null, null);
    }

    /**
     * Back-compat overload — agent requirements without tenant context.
     * Useful for unit tests that exercise the dep-install path without
     * touching the tenant filesystem layout.
     */
    public TurCustomToolCodeHelper(TurCodeInterpreterToolService codeInterpreter,
            String agentRequirements) {
        this(codeInterpreter, agentRequirements, null, null);
    }

    /**
     * Full constructor used in production by {@link TurCustomToolCallbackService}.
     *
     * @param codeInterpreter   platform's Python sandbox manager
     * @param agentRequirements active agent's {@code pythonRequirements}
     *                          addendum; null/blank for global-only
     * @param agentId           active agent id for path-isolated session dir;
     *                          null falls back to legacy flat {@code sessions/}
     * @param conversationId    active conversation id for path-isolated
     *                          session dir; null falls back to legacy layout
     */
    public TurCustomToolCodeHelper(TurCodeInterpreterToolService codeInterpreter,
            String agentRequirements, String agentId, String conversationId) {
        this.codeInterpreter = codeInterpreter;
        this.agentRequirements = agentRequirements;
        this.agentId = agentId;
        this.conversationId = conversationId;
    }

    /**
     * Executes a Python script in the platform sandbox with the union of
     * global + agent-specific Python requirements available on
     * {@code PYTHONPATH}, and the session dir scoped to the active
     * tenant when context is known. Returns the markdown result produced
     * by {@link TurCodeInterpreterToolService}.
     *
     * <p>30-second timeout enforced; longer scripts must split work or be
     * refactored.
     *
     * @param pythonCode complete script source. Inputs must be inlined
     *                   (the sandbox does not accept stdin); idiomatic
     *                   pattern is to dump the inputs as base64-encoded
     *                   JSON at the top of the script and {@code json.loads}
     *                   them inside.
     */
    public String executePython(String pythonCode) {
        if (pythonCode == null || pythonCode.isBlank()) {
            return "Error: empty Python code";
        }
        return codeInterpreter.executePythonForTenant(
                pythonCode, agentRequirements, agentId, conversationId);
    }

    /**
     * Named-argument overload — the idiomatic Groovy call pattern is:
     *
     * <pre>{@code
     *   def result = code.executePython(
     *       script: '''
     *           # use INPUTS as a regular Python dict
     *           print("Empresa:", INPUTS["proposta"]["empresa"])
     *           # … reportlab / matplotlib / pandas …
     *       ''',
     *       inputs: [proposta: parsed, share_url_b2b: shareUrl, hash: inputsHash]
     *   )
     * }</pre>
     *
     * The helper JSON-encodes + base64-wraps {@code inputs} and prepends a
     * 3-line prelude to {@code script} that decodes them back into a global
     * dict named {@code INPUTS}. Eliminates the brittle
     * {@code '''heredoc''' + var + '''heredoc'''} pattern that Groovy's
     * antlr4 parser chokes on inside method bodies (script-level it works,
     * but the moment the heredoc lives inside a {@code def myFn() &#123; … &#125;}
     * the parser emits {@code Unexpected character: '\''} on the close+reopen
     * sequence).
     *
     * <p>The prelude uses underscore-prefixed import aliases
     * ({@code _json}, {@code _base64}) so the user's script can re-import
     * {@code json} or {@code base64} normally without name clash.
     *
     * <p>{@code inputs} accepts any Jackson-serializable value: nested
     * maps, lists, primitives, JSON-typed Groovy objects. Pass {@code null}
     * or omit the key to skip the prelude entirely (the call degenerates
     * to {@link #executePython(String)}).
     *
     * @param namedArgs Groovy-style named-arg map. Required key: {@code
     *                  script} (String). Optional key: {@code inputs}
     *                  (Map or any Jackson-serializable).
     * @return same markdown payload as {@link #executePython(String)}
     */
    public String executePython(Map<String, Object> namedArgs) {
        String script;
        try {
            script = assembleScript(namedArgs);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
        return executePython(script);
    }

    /**
     * Structured sibling of {@link #executePython(String)} (T83) — returns the
     * typed {@link TurCodeInterpreterToolService.TurCodeInterpreterResult}
     * (stdout / stderr / files / exitCode / timedOut / durationMs) so the
     * Groovy author reads fields directly instead of regex-parsing the
     * markdown. Empty code yields a failed result (no subprocess run).
     */
    public TurCodeInterpreterToolService.TurCodeInterpreterResult executePythonStructured(String pythonCode) {
        if (pythonCode == null || pythonCode.isBlank()) {
            return failed("Error: empty Python code");
        }
        return codeInterpreter.executePythonStructuredForTenant(
                pythonCode, agentRequirements, agentId, conversationId);
    }

    /**
     * Named-argument structured overload (T83) — same {@code script:} /
     * {@code inputs:} contract as {@link #executePython(Map)} but returns the
     * structured result. Bad input yields a failed result whose {@code stderr}
     * carries the diagnostic.
     */
    public TurCodeInterpreterToolService.TurCodeInterpreterResult executePythonStructured(
            Map<String, Object> namedArgs) {
        String script;
        try {
            script = assembleScript(namedArgs);
        } catch (IllegalArgumentException e) {
            return failed("Error: " + e.getMessage());
        }
        return executePythonStructured(script);
    }

    /**
     * JSON form of {@link #executePythonStructured(String)} (T83). Returns a
     * compact object {@code {session_id, success, exit_code, timed_out,
     * duration_ms, stdout, stderr, files:[{name,url,image,size_bytes}]}} —
     * snake_case keys, the {@code markdown} field deliberately omitted — ready
     * for {@code JsonSlurper} or a webhook payload.
     */
    public String executePythonJson(String pythonCode) {
        return toJson(executePythonStructured(pythonCode));
    }

    /** Named-argument JSON overload (T83). See {@link #executePythonJson(String)}. */
    public String executePythonJson(Map<String, Object> namedArgs) {
        return toJson(executePythonStructured(namedArgs));
    }

    /**
     * Builds the final Python source from the named-arg map shared by the
     * markdown and structured/JSON paths. Returns {@code script} unchanged when
     * no {@code inputs} are supplied, otherwise prepends the base64-JSON
     * decode prelude that exposes the {@code INPUTS} dict.
     *
     * @throws IllegalArgumentException with a caller-facing message (no
     *         {@code "Error: "} prefix — callers add their own) when the map is
     *         null, {@code script} is missing/blank, or {@code inputs} can't be
     *         JSON-encoded.
     */
    private String assembleScript(Map<String, Object> namedArgs) {
        if (namedArgs == null) {
            throw new IllegalArgumentException("missing named args (script:, inputs:)");
        }
        Object scriptObj = namedArgs.get("script");
        if (!(scriptObj instanceof String script) || script.isBlank()) {
            throw new IllegalArgumentException("missing or empty 'script' argument");
        }
        Object inputs = namedArgs.get("inputs");
        if (inputs == null) {
            return script;
        }
        String json;
        try {
            json = INPUTS_JSON.writeValueAsString(inputs);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to JSON-encode 'inputs': " + e.getMessage());
        }
        String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        // The prelude exposes json/base64 under both names — the
        // underscore-prefixed alias for our own decode call, AND the
        // bare module name that user scripts overwhelmingly expect to
        // be available without a re-import. Earlier we exposed only
        // {@code _json}, which created a silent footgun: scripts that
        // called {@code json.loads(...)} hit NameError, often swallowed
        // by a generic {@code except Exception:} branch that defaulted
        // to {@code []} — the data looked empty when it was actually
        // present. Importing under both names costs nothing (Python
        // caches module imports) and removes the trap.
        // The b64 string is safe to embed in a Python double-quoted literal —
        // the base64 alphabet (A-Z a-z 0-9 + / =) doesn't contain " or \\.
        String prelude = "import json as _json\n"
                + "import json\n"
                + "import base64 as _base64\n"
                + "import base64\n"
                + "INPUTS = _json.loads(_base64.b64decode(\"" + b64 + "\").decode(\"utf-8\"))\n\n";
        return prelude + script;
    }

    /**
     * Failed structured result for input-validation errors that happen before
     * any subprocess runs — mirrors the service's own error result shape so a
     * Groovy author sees {@code success() == false} and the diagnostic in both
     * {@code stderr()} and {@code markdown()}.
     */
    private static TurCodeInterpreterToolService.TurCodeInterpreterResult failed(String message) {
        return new TurCodeInterpreterToolService.TurCodeInterpreterResult(
                null, false, -1, false, 0L, "", message, java.util.List.of(), message);
    }

    /**
     * Serializes a structured result to the T83 JSON contract. The
     * {@code markdown} field is intentionally excluded (it's the legacy
     * rendering, redundant for JSON consumers) and keys are snake_case so the
     * shape matches {@code {stdout, files, duration_ms, exit_code}} as
     * documented. Never throws — a serialization failure degrades to a small
     * error object so a Groovy caller always gets parseable JSON.
     */
    private static String toJson(TurCodeInterpreterToolService.TurCodeInterpreterResult r) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("session_id", r.sessionId());
        m.put("success", r.success());
        m.put("exit_code", r.exitCode());
        m.put("timed_out", r.timedOut());
        m.put("duration_ms", r.durationMs());
        m.put("stdout", r.stdout());
        m.put("stderr", r.stderr());
        java.util.List<java.util.Map<String, Object>> files = new java.util.ArrayList<>();
        for (TurCodeInterpreterToolService.TurCodeInterpreterFile f : r.files()) {
            java.util.Map<String, Object> fm = new java.util.LinkedHashMap<>();
            fm.put("name", f.name());
            fm.put("url", f.url());
            fm.put("image", f.image());
            fm.put("size_bytes", f.sizeBytes());
            files.add(fm);
        }
        m.put("files", files);
        try {
            return INPUTS_JSON.writeValueAsString(m);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"failed to serialize result\"}";
        }
    }
}
