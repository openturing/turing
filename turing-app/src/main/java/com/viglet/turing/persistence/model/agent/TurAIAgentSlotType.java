/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.agent;

/**
 * Primitive types supported by {@link TurAIAgentSlot}. Chat-flow nodes that
 * write into a slot (currently the {@code outputVariable} of askQuestion
 * nodes) must produce a value compatible with the slot's declared type.
 *
 * <p>{@code STRING} is a single line of text; {@code TEXT} is multi-line /
 * long-form. Both are stored as {@code String} at runtime — the distinction
 * is purely UX-level (how the value is rendered/edited in tooling).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public enum TurAIAgentSlotType {
    STRING,
    INTEGER,
    BOOLEAN,
    FLOAT,
    TEXT
}
