/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.answer;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;
import com.viglet.turing.genai.answer.TurAnswerSignal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T845 / §LXII.14 — one answer-level interaction signal (citation clicked, facet
 * refined after reading, follow-up taken, re-answer, thumbs) captured from the
 * answer surface and keyed by the answer's effective {@code agentId} so the
 * {@code selftuning} miner can consume it alongside the chat-analytics session
 * failures.
 *
 * <p>Modeled as a dedicated append-only table (mirroring the T170
 * {@code chat_turn_feedback} DPO table) rather than widening the chat-analytics
 * session document — the answer surface is stateless SN-site streaming with no
 * session lifecycle, and the persisted {@link #relevanceMiss} flag lets the miner
 * count misses with a single indexed query.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "tur_answer_signal",
        indexes = @Index(name = "ix_tur_answer_signal_agent", columnList = "agentId"))
public class TurAnswerSignalEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** The effective agent that produced the answer — the miner's key. */
    @Column(name = "agentId", nullable = false, length = 36)
    private String agentId;

    /** The SN site the answer was produced over. */
    @Column(name = "siteName", length = 255)
    private String siteName;

    /** The answer interaction this signal belongs to (correlates several signals). */
    @Column(name = "conversationId", length = 128)
    private String conversationId;

    /** The question the answer answered (context for the failure digest). */
    @Lob
    @Column(name = "query", columnDefinition = "longtext")
    private String query;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal", nullable = false, length = 32)
    private TurAnswerSignal signal;

    /**
     * Denormalized copy of {@link TurAnswerSignal#isRelevanceMiss()} so the miner
     * can pull an agent's recent misses with one indexed query rather than
     * filtering every signal in memory.
     */
    @Column(name = "relevanceMiss", nullable = false)
    private boolean relevanceMiss;

    @Column(name = "createdAt", nullable = false)
    private Instant createdAt = Instant.now();
}
