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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One speaker in a {@link TurPersonaDialogueProject}'s roster (Block AU /
 * §XLIV). {@code speakerOrder} is significant — it is the round-robin speaking
 * order. {@code personaId} is a <em>soft</em> reference to {@code ai_persona}
 * (resolved at read), so a renamed/removed persona degrades gracefully.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "ai_persona_dialogue_speaker")
public class TurPersonaDialogueSpeaker implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 40)
    private String id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    @JsonIgnore
    private TurPersonaDialogueProject project;

    /** Soft reference to an existing {@code ai_persona.id}. */
    @Column(name = "persona_id", nullable = false, length = 40)
    private String personaId;

    /** Position in the round-robin roster (0-based). */
    @Column(name = "speaker_order", nullable = false)
    private int speakerOrder;
}
