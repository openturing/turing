/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.persona.dialogue;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A Persona Dialogue project (Block AU / §XLIV) — a reusable, saved dialogue
 * setup, the direct analogue of a Persona Match project. Instead of an ephemeral
 * "pick a topic + speakers + run" form, a project persists its <b>topic</b>,
 * <b>ordered speaker roster</b> ({@link TurPersonaDialogueSpeaker}), model and
 * turn budget so it can be re-opened and re-run; the last streamed transcript is
 * persisted as {@link TurPersonaDialogueTurn}s for review.
 *
 * <p>Admin-CRUD aggregate — plain JPA repositories (no domain records/ports, per
 * the Block AF stop-criteria). The round-robin streaming engine
 * ({@code TurPersonaDialogueService}) is reused unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "ai_persona_dialogue_project")
public class TurPersonaDialogueProject implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 40)
    private String id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    /** The subject that seeds the first speaker's opening turn. */
    @Column(name = "topic", length = 2000)
    private String topic;

    /** LLM instance driving every turn; {@code null} falls back at run time. */
    @Column(name = "llmInstanceId", length = 40)
    private String llmInstanceId;

    /**
     * Number of <b>rounds</b> (one round = every persona speaks once, in order).
     * The run budget is {@code rounds × speakers} total utterances. Default 5,
     * clamped [1, 20] by the service.
     */
    @Column(name = "turns", nullable = false)
    private int turns = 5;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "creationDate")
    private Instant creationDate;

    @Column(name = "modificationDate")
    private Instant modificationDate;
}
