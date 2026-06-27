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

import java.time.Instant;
import java.util.List;

import com.viglet.turing.persistence.model.agent.TurChatCitationRecord;

/**
 * §X.7.d / T155 — wire shape of the citation-drift endpoint
 * ({@code GET /api/chat/sessions/{conversationId}/citation-drift}). Lists every
 * persisted citation for the conversation with its current drift verdict, so an
 * operator can answer "what document did this answer cite, and has it changed
 * since?". {@code staleCount} is the headline number for the audit badge.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurChatCitationDriftDto(String conversationId, long staleCount, List<Entry> entries) {

    public record Entry(
            String id,
            String sourceId,
            String documentTitle,
            String url,
            String citedText,
            Instant answerProducedAt,
            boolean citationStale,
            String staleReason,
            Instant lastCheckedAt,
            Instant driftDetectedAt) {

        public static Entry of(TurChatCitationRecord r) {
            return new Entry(r.getId(), r.getSourceId(), r.getDocumentTitle(), r.getUrl(),
                    r.getCitedText(), r.getAnswerProducedAt(), r.isCitationStale(),
                    r.getStaleReason(), r.getLastCheckedAt(), r.getDriftDetectedAt());
        }
    }

    public static TurChatCitationDriftDto from(String conversationId,
            List<TurChatCitationRecord> records) {
        List<Entry> entries = records.stream().map(Entry::of).toList();
        long staleCount = records.stream().filter(TurChatCitationRecord::isCitationStale).count();
        return new TurChatCitationDriftDto(conversationId, staleCount, entries);
    }
}
