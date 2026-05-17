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
 * <p>Usage in Groovy:
 * <pre>{@code
 *   def result = code.executePython('''
 *       from reportlab.platypus import SimpleDocTemplate
 *       # ... build a PDF
 *       print('done')
 *   ''')
 *   def m = (result =~ /\[Download[^\]]+]\(([^)]+)\)/)
 *   if (m) slots.set("proposta_pdf_url", m[0][1])
 * }</pre>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public class TurCustomToolCodeHelper {

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
}
