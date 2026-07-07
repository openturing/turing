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
 * T618 / §XXXIV.6 — one selectable entry in the Live Preview's "captured turn"
 * picker. Populated from the per-turn prompt-capture store so the operator can
 * load any past turn of a conversation verbatim (as opposed to the T612
 * current-state replay).
 *
 * @param turnIndex  1-based turn number within the conversation.
 * @param capturedAt epoch millis when the turn was captured ({@code 0} when the
 *                   storage backend reported no parseable timestamp).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSystemPromptCapturedTurnDto(int turnIndex, long capturedAt) {
}
