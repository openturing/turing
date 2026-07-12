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
 * One persisted utterance of a {@link TurPersonaDialogueProject}'s last run
 * (Block AU / §XLIV), so re-opening a project shows the last conversation — the
 * analogue of Persona Match persisting its matrix cells. A run replaces the
 * whole transcript. {@code projectId} is a plain column (rows are written/read
 * in bulk, not navigated).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "ai_persona_dialogue_turn",
        indexes = @Index(name = "idx_persona_dialogue_turn_project", columnList = "project_id"))
public class TurPersonaDialogueTurn implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 40)
    private String id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    @Column(name = "project_id", nullable = false, length = 40)
    private String projectId;

    @Column(name = "turn_index", nullable = false)
    private int turnIndex;

    @Column(name = "persona_id", length = 40)
    private String personaId;

    @Column(name = "persona_name", length = 200)
    private String personaName;

    @Lob
    @Column(name = "content")
    private String content;
}
