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
 * T122 / §IX.6.b — one suspended conversation in the parked-conversations
 * dashboard. A conversation is "parked" when one of its chat-flow state rows
 * has its cursor sitting on a {@code suspend} node (T121); the engine replies
 * with a waiting banner and does not run an LLM turn until {@code POST
 * .../chat/resume} (or the admin unblock action) walks the cursor past it.
 *
 * @param conversationId the suspended conversation's opaque id (the SDK
 *                       session id) — the key the unblock action resumes
 * @param agentId        owning agent id of the parked flow, or {@code null}
 * @param agentTitle     resolved agent title, or {@code null} when the agent
 *                       no longer exists in the catalog
 * @param flowId         the flow whose state is parked
 * @param flowName       human-readable flow name
 * @param nodeId         id of the {@code suspend} node the cursor sits on
 * @param reason         the suspend node's label (the {@code flow.suspend(...)}
 *                       reason string), or {@code "suspended"} when unlabeled
 * @param since          when the state last advanced onto the suspend node
 *                       (ISO-8601 local date-time), i.e. the start of the wait
 * @param waitingSeconds seconds elapsed since {@code since} — how long the
 *                       conversation has been blocked
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurParkedConversationDto(
        String conversationId,
        String agentId,
        String agentTitle,
        String flowId,
        String flowName,
        String nodeId,
        String reason,
        String since,
        long waitingSeconds) {
}
