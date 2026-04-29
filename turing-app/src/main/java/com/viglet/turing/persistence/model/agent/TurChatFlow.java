/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Chat flow authored in the React Flow editor, scoped to a single AI agent.
 * The graph (nodes + edges) is serialized into {@link #definitionJson} so the
 * runtime advisor (Phase B) can deserialize it and walk the state machine.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Getter
@Setter
@Entity
@Table(name = "chat_flow")
public class TurChatFlow implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Lob
    @Column(name = "definitionJson", columnDefinition = "longtext")
    private String definitionJson;

    @Column(nullable = false)
    private int enabled = 1;

    /**
     * How the runtime engine enforces this flow. The column lets each flow
     * pick its own guard rail strategy as new methodologies (LLM-as-judge,
     * structured output, …) ship in later phases.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "guardrailMethod", nullable = false, length = 32)
    private TurChatFlowGuardrailMethod guardrailMethod = TurChatFlowGuardrailMethod.HEURISTIC;

    /**
     * Natural-language description that the LLM router uses to decide whether
     * a user message should auto-trigger this flow. Empty/blank disables
     * auto-trigger — the flow can still be picked manually from the chat
     * dropdown.
     */
    @Lob
    @Column(name = "triggerDescription", columnDefinition = "longtext")
    private String triggerDescription;

    /** How often the router may auto-trigger this flow per conversation. */
    @Enumerated(EnumType.STRING)
    @Column(name = "triggerMode", nullable = false, length = 16)
    private TurChatFlowTriggerMode triggerMode = TurChatFlowTriggerMode.ONCE;

    /**
     * Owning AI agent. The client always knows the agent id from the URL, so
     * the back-reference is hidden from JSON to keep the payload small and
     * avoid serializing the full agent graph.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    @JsonIgnore
    private TurAIAgent turAIAgent;
}
