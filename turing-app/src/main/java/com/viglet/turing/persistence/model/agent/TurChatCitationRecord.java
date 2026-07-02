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

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * §X.7.d / T155 — one persisted Anthropic Citation (T152–T154), captured at
 * answer time so a background job can later re-resolve it and detect drift.
 *
 * <p>When an agent answers with the {@code citations} request option on, every
 * per-sentence citation Claude returns is written here together with the
 * question that produced it and the instant the answer was generated. The daily
 * {@code TurCitationDriftDetectionJob} re-retrieves recent records against the
 * live index and flips {@link #citationStale} when:
 * <ul>
 *   <li>the cited source has been <b>re-indexed since the answer</b> — its
 *       {@code modification_date} in the current index is newer than
 *       {@link #answerProducedAt}; or</li>
 *   <li>the exact {@link #citedText} span is <b>no longer present</b> in the
 *       source's current chunks (the content changed underneath the citation).</li>
 * </ul>
 *
 * <p>The row therefore turns Turing's provable provenance into an auditable,
 * time-aware record: the compliance question "what document did this answer cite,
 * and has it changed since?" is answered by {@link #citationStale} +
 * {@link #driftDetectedAt}. Conversation-scoped and append-then-annotate (the
 * scan only updates the verdict columns); dropped wholesale by the LGPD/GDPR
 * "forget visitor" path so it never outlives its conversation.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "chat_citation_record",
        indexes = {
                @Index(name = "ix_chat_citation_record_conversation",
                        columnList = "conversationId"),
                @Index(name = "ix_chat_citation_record_scan",
                        columnList = "citationStale,answerProducedAt")
        })
public class TurChatCitationRecord implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "conversationId", nullable = false, length = 100)
    private String conversationId;

    @Column(name = "agentId", length = 36)
    private String agentId;

    /** The user question that produced this answer — replayed to re-retrieve. */
    @Lob
    @Column(name = "question", columnDefinition = "longtext")
    private String question;

    /** Stable id of the cited source (SN {@code source_id} / asset objectName). */
    @Column(name = "sourceId", nullable = false, length = 512)
    private String sourceId;

    @Column(name = "documentTitle", length = 512)
    private String documentTitle;

    @Lob
    @Column(name = "url", columnDefinition = "longtext")
    private String url;

    /** The exact span Claude grounded the claim on — the drift probe. */
    @Lob
    @Column(name = "citedText", columnDefinition = "longtext")
    private String citedText;

    /** Top-K used to retrieve passages at answer time (re-check widens this). */
    @Column(name = "topK")
    private Integer topK;

    @Column(name = "answerProducedAt", nullable = false)
    private Instant answerProducedAt;

    @Column(name = "citationStale", nullable = false)
    private boolean citationStale = false;

    @Column(name = "staleReason", length = 512)
    private String staleReason;

    /** When the scan last re-resolved this citation ({@code null} until checked). */
    @Column(name = "lastCheckedAt")
    private Instant lastCheckedAt;

    /** When drift was first detected ({@code null} while still fresh). */
    @Column(name = "driftDetectedAt")
    private Instant driftDetectedAt;
}
