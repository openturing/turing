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
import java.util.Map;

/**
 * Snapshot of one finished chat-flow run, exposed by the submissions API
 * so the front-end History page can list captured values per conversation.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
public record TurChatFlowSubmissionDto(
        String conversationId,
        String flowId,
        LocalDateTime completedAt,
        Map<String, String> variables,
        String userId,
        String endNodeId) {
}
