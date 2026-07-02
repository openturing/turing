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
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Typed variable declared on an AI agent. Chat-flow editors pick from the
 * agent's slot catalog instead of letting authors type free-form
 * {@code outputVariable} names — guaranteeing that values collected across
 * flows land in a known schema (name + primitive type) so they can be
 * serialised into long-term memory or surfaced via the chat session APIs.
 *
 * <p>Slots are owned by exactly one agent (one-to-many from
 * {@link TurAIAgent}); {@code name} is unique within that scope.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Getter
@Setter
@Entity
@Table(name = "ai_agent_slot",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ai_agent_slot_agent_name",
                columnNames = { "agent_id", "name" }))
public class TurAIAgentSlot implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private TurAIAgentSlotType type = TurAIAgentSlotType.STRING;

    /**
     * T100 — when {@code true}, this slot is a default target of document
     * extraction ({@code POST /chat/slot-extract}) even when the caller passes
     * no explicit {@code slotNames}. The set of slots flagged this way is the
     * agent's <em>document schema</em>. Backwards-compatible opt-in: when an
     * agent has <em>no</em> flagged slot, extraction falls back to the legacy
     * behaviour of targeting the whole slot catalog.
     */
    @Column(name = "extract_from_document", nullable = false)
    private boolean extractFromDocument = false;

    /**
     * T100 — optional Java regex an extracted value must fully match to be
     * persisted. When set, document/vision extraction drops a value that does
     * not match (logged, not written) so malformed fills never reach the slot.
     * Also surfaced to the LLM as a formatting hint in the extraction prompt.
     * Null/blank means no validation.
     */
    @Column(name = "validation_pattern", length = 500)
    private String validationPattern;

    /**
     * Owning AI agent. Hidden from JSON because the client always knows the
     * agent id from the URL and we don't want to serialise the full agent
     * graph on every list response.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    @JsonIgnore
    private TurAIAgent turAIAgent;
}
