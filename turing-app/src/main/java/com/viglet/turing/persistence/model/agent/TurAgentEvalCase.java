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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T285 / §XV.1 — one golden-set case of an {@link TurAgentEvalSet}. Each case
 * is a scripted conversation ({@code seedTurnsJson}, a JSON array of user
 * messages) plus what the agent is expected to do with it:
 *
 * <ul>
 *   <li>{@code expectedSlotsJson} — JSON map of {@code slotName -> value} the
 *       replay must capture (deterministic equality diff).</li>
 *   <li>{@code expectedOutcome} — terminal outcome label (CAPTURED / ABANDONED
 *       / HANDOFF / ANY).</li>
 *   <li>{@code expectedNodeId} — the flow node the conversation should land on
 *       (optional cursor assertion).</li>
 *   <li>{@code rubric} — an optional natural-language assertion scored by the
 *       bilingual LLM judge (semantic check beyond deterministic diffs).</li>
 * </ul>
 *
 * <p>Cases are seedable from anonymized real sessions — a green production
 * transcript minus PII (T61) becomes a regression fixture.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
@Entity
@Table(name = "tur_agent_eval_case")
public class TurAgentEvalCase implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    /** JSON array of the scripted user turns, replayed in order. */
    @Column(name = "seedTurnsJson", columnDefinition = "longtext")
    private String seedTurnsJson;

    /** JSON map {@code slotName -> expected value} asserted after the replay. */
    @Column(name = "expectedSlotsJson", columnDefinition = "longtext")
    private String expectedSlotsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "expectedOutcome", nullable = false, length = 16)
    private TurAgentEvalExpectedOutcome expectedOutcome = TurAgentEvalExpectedOutcome.ANY;

    /** Optional cursor assertion — the node the flow should land on. */
    @Column(name = "expectedNodeId", length = 200)
    private String expectedNodeId;

    /** Optional natural-language rubric scored by the bilingual LLM judge. */
    @Column(name = "rubric", columnDefinition = "longtext")
    private String rubric;

    @Column(name = "sortOrder", nullable = false)
    private int sortOrder = 0;

    /**
     * Owning eval set. Hidden from JSON because the client always knows the
     * set from the URL and we don't want to serialise the parent graph on
     * every list response.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eval_set_id")
    @JsonIgnore
    private TurAgentEvalSet turAgentEvalSet;
}
