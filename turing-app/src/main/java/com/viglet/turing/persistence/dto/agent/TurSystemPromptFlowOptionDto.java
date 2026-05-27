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
 * One chat flow an operator can pick in the System Prompt Live Preview's flow
 * selector — so they can see how the assembled prompt changes when that flow
 * governs the turn.
 *
 * @param id      the chat flow id.
 * @param name    the chat flow display name.
 * @param enabled whether the flow is enabled (disabled flows never govern a
 *                real turn, but can still be previewed).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurSystemPromptFlowOptionDto(
        String id,
        String name,
        boolean enabled) {
}
