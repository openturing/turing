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
 * T94 / §VII.11.d — one finding from
 * {@link com.viglet.turing.genai.flow.TurChatFlowLinterService}. The admin
 * editor renders these in a sidebar panel so authoring problems surface at
 * edit time instead of waiting for a real chat turn to expose them.
 *
 * @param nodeId    id of the node the issue points at; {@code null} when the
 *                  issue is flow-scoped (cross-flow trigger ambiguity).
 * @param edgeId    id of the offending edge ({@code edge_without_label});
 *                  {@code null} otherwise.
 * @param severity  {@code "ERROR"} (flow is likely broken at runtime),
 *                  {@code "WARNING"} (works today but should be fixed),
 *                  {@code "INFO"} (style nit, FYI).
 * @param code      stable machine-readable identifier so the frontend can
 *                  filter / theme issues consistently
 *                  (e.g. {@code ai_instruction_too_long}).
 * @param message   one-line human description of what's wrong.
 * @param hint      one-line actionable suggestion the author can act on.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurChatFlowLintIssueDto(
        String nodeId,
        String edgeId,
        String severity,
        String code,
        String message,
        String hint) {
}
