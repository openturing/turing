/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

/**
 * One finding from the AI Agent system-prompt validator. Mirrors
 * {@link TurChatFlowLintIssueDto} (the chat-flow "authoring warnings" pattern)
 * but swaps the flow-specific {@code nodeId}/{@code edgeId} for a {@code source}
 * tag pointing at the prompt fragment the issue concerns — so the same warnings
 * UI renders both, and the operator can tell whether a conflict comes from the
 * agent prompt, the persona, an MCP server, or the deep LLM analysis.
 *
 * @param severity {@code "ERROR"} (likely to break or confuse the LLM),
 *                 {@code "WARNING"} (works but risky), {@code "INFO"} (FYI).
 * @param code     stable machine-readable identifier the frontend themes on
 *                 (e.g. {@code language_directive_conflict}).
 * @param message  one-line human description of the problem (English; the
 *                 frontend prefers a {@code code}-keyed translation and only
 *                 falls back to this. Deep-LLM findings are produced in the
 *                 system prompt's own language, so they stay verbatim).
 * @param hint     one-line actionable suggestion (English fallback).
 * @param source   which fragment the issue concerns: {@code AGENT},
 *                 {@code PERSONA}, {@code MCP}, {@code FLOW}, or {@code LLM}
 *                 (deep analysis). Free text — may include a concrete name.
 * @param params   interpolation values for the {@code code}-keyed i18n
 *                 template (e.g. {@code length}, {@code langs}, {@code term}).
 *                 Never {@code null} — empty when the message is fully static.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurSystemPromptIssueDto(
        String severity,
        String code,
        String message,
        String hint,
        String source,
        java.util.Map<String, String> params) {

    /** Convenience overload for findings whose message carries no dynamic values. */
    public TurSystemPromptIssueDto(String severity, String code, String message,
            String hint, String source) {
        this(severity, code, message, hint, source, java.util.Map.of());
    }
}
