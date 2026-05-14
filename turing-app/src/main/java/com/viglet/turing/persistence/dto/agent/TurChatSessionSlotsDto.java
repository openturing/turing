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

import java.util.Map;

/**
 * Response of the session-slots API. Slots are conversation-scoped — even
 * though each flow row in {@code chat_flow_state} / {@code chat_flow_submission}
 * persists its own snapshot, every flow in a single AI agent shares the same
 * slot namespace, so the API exposes them merged at the JSON root rather than
 * grouped per flow.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public record TurChatSessionSlotsDto(
        String conversationId,
        Map<String, String> slots) {
}
