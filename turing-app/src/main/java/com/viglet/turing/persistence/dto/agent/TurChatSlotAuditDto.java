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

import java.time.LocalDateTime;
import java.util.List;

/**
 * Wire shape of the T60 slot audit endpoint
 * ({@code GET /api/chat/sessions/{conversationId}/slot-audit}). Returns the
 * full timeline of slot writes for the conversation, oldest first.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurChatSlotAuditDto(String conversationId, List<Entry> entries) {

    public record Entry(
            String id,
            String slotName,
            String oldValue,
            String newValue,
            String source,
            String originDetail,
            LocalDateTime ts) {
    }
}
