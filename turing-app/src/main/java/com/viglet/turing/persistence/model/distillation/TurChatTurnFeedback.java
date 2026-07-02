/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.distillation;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.viglet.turing.genai.distillation.TurChatFeedbackRating;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

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
 * F.9 / §X.10.d — T170. One operator thumb-up/down on an assistant turn.
 *
 * <p>The binary preference signal the DPO dataset is built from: a {@link #rating}
 * over the {@link #userPrompt} → {@link #assistantAnswer} pair. {@code TurDpoDatasetBuilder}
 * pairs a thumb-up ("chosen") answer with a thumb-down ("rejected") answer for the
 * same prompt to form a DPO preference example.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "chat_turn_feedback",
        indexes = @Index(name = "ix_chat_turn_feedback_agent", columnList = "agentId"))
public class TurChatTurnFeedback implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** The agent whose answer was rated. */
    @Column(name = "agentId", nullable = false, length = 36)
    private String agentId;

    /** The conversation the rated turn belongs to (optional context). */
    @Column(name = "conversationId", length = 128)
    private String conversationId;

    /** The user prompt the assistant answered (the DPO example's input). */
    @Lob
    @Column(name = "userPrompt", columnDefinition = "longtext", nullable = false)
    private String userPrompt;

    /** The assistant answer being rated (chosen or rejected, per {@link #rating}). */
    @Lob
    @Column(name = "assistantAnswer", columnDefinition = "longtext", nullable = false)
    private String assistantAnswer;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating", nullable = false, length = 8)
    private TurChatFeedbackRating rating;

    @Column(name = "createdAt", nullable = false)
    private Instant createdAt = Instant.now();
}
