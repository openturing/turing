/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.authoring;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Generic chat request body for the AI Authoring pattern.
 *
 * <p>{@code messages} is the conversation history (oldest first). The last
 * entry is normally the new user message. {@code currentState} is the
 * frontend's snapshot of whatever entity is being authored — the LLM sees it
 * each turn so it can respect any manual edits the user has already made in
 * the form.
 *
 * @param <T> entity-specific shape (e.g. {@code IntentGeneration},
 *            {@code AiAgentGeneration})
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiAuthoringRequest<T>(List<AiAuthoringMessage> messages, T currentState) {
}
