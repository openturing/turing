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
 * <p>{@code IMAGE}, {@code AUDIO}, {@code VIDEO} and {@code FILE} are
 * <em>multi-modal</em> slots (T64): the runtime value is not the binary itself
 * but a URL pointing at the binary kept in object storage
 * ({@code TurStorageService} — MinIO / S3 / filesystem). A
 * {@code POST .../chat/slot-upload} stores the upload and writes the resolved
 * URL into the slot; Custom Tools and the Vision/Audio/Video extraction path
 * resolve that URL back to bytes when they need the content (e.g. photo-of-CV →
 * Vision LLM → text slots; or T501 video → Gemini → timestamped transcript +
 * scene description).
 *
 * <p>{@code VIDEO} (T501 / §X.19) rides Gemini's native video understanding:
 * the uploaded clip (or a YouTube URL) is passed to Gemini for a timestamped
 * transcript + scene/visual description, written back to a target text slot.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public enum TurAIAgentSlotType {
    STRING,
    INTEGER,
    BOOLEAN,
    FLOAT,
    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    FILE;

    /**
     * Multi-modal slots hold a URL to a binary in object storage rather than
     * an inline scalar value. Callers that upload / resolve binaries gate on
     * this so a scalar slot can never be targeted by the binary upload path.
     *
     * @since 2026.3.1
     */
    public boolean isMultiModal() {
        return this == IMAGE || this == AUDIO || this == VIDEO || this == FILE;
    }
}
