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
 * Origin of a slot write captured by the {@code chat_slot_write_log} audit
 * table. The engine instruments every persistence path so admins can replay
 * "who wrote this value and when" without spelunking server logs — relevant
 * both for LGPD/GDPR auditing of personal data captured during a chat and
 * for general debugging of flow misbehaviour.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurChatSlotAuditSource {
    /** Slot set by a chat-flow node executor (slot, writeSlot, formCapture, etc.). */
    NODE,
    /** Slot set by a Custom Tool Groovy script via {@code slots.set(...)}. */
    TOOL,
    /** Slot set by {@code POST /api/sn/{site}/chat/slots} (React {@code useTuringSlotWriter}). */
    ENDPOINT,
    /** Slot set by {@code POST /api/sn/{site}/chat/slot-extract} (document → LLM → slot). */
    EXTRACT,
    /**
     * Multi-modal binary persisted to object storage and its URL written into
     * an {@code IMAGE}/{@code AUDIO}/{@code FILE} slot via
     * {@code POST /api/sn/{site}/chat/slot-upload} (T64).
     */
    UPLOAD
}
