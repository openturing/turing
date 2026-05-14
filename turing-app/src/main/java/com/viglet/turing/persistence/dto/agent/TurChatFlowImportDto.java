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
package com.viglet.turing.persistence.dto.agent;

import java.util.List;

import com.viglet.turing.persistence.dto.persona.TurPersonaDto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * Request body for the chat-flow import endpoint. Bundles the flow itself
 * with the personas referenced by its {@code persona} nodes — the import
 * pipeline auto-creates any persona whose name is not already in the
 * catalog and attaches every resolved persona to the target AI agent.
 *
 * <p>Personas are matched <em>by name</em>, case-insensitively. Each
 * embedded persona carries its <em>original</em> {@code id} so the import
 * pipeline can rewrite the {@code personaId} references inside the flow's
 * {@code definitionJson} once the persistence layer has assigned real
 * UUIDs to the freshly-created entities.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Getter
@Setter
@NoArgsConstructor
public class TurChatFlowImportDto {

    /** The chat flow to import — all fields except {@code id} are honored. */
    private TurChatFlowDto chatFlow;

    /**
     * Personas embedded alongside the flow. Optional; when absent the import
     * behaves exactly like a regular create.
     */
    private List<TurPersonaDto> personas;

    /**
     * Slots embedded alongside the flow. Optional. Each entry whose
     * {@code name} is not yet declared on the target agent is auto-created
     * before the flow is saved, so {@code outputVariable} fields inside the
     * graph resolve to a real slot in the catalogue. Existing slots are left
     * untouched — names match case-insensitively within the agent scope.
     *
     * @since 2026.2.7
     */
    private List<TurAIAgentSlotDto> slots;
}
