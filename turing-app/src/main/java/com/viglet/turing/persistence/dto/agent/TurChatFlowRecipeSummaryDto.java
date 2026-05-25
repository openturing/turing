/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

import java.util.List;

/**
 * T96 / §VII.11.f — listing entry for the chat-flow recipe marketplace. Carries
 * only the metadata the catalog UI needs (title, description, vertical tag,
 * counts of bundled assets) so a {@code GET /api/chat-flow-recipes} request
 * stays cheap even when a recipe ships dozens of nodes / personas.
 *
 * <p>The full bundle (every flow, persona and slot the install will create)
 * is fetched on demand via {@code GET /api/chat-flow-recipes/{id}} when the
 * operator opens the preview panel.
 *
 * @param id          stable kebab-case identifier ({@code lead-capture-b2c}) —
 *                    URLs, install audit trail, version tracking all key on this.
 * @param version     semantic version of this recipe (e.g. {@code 1.0.0}).
 *                    Installed flows record this so future "upgrade available"
 *                    UX can diff customer forks against upstream changes.
 * @param name        human-readable title for the catalog card.
 * @param description one-paragraph pitch — what problem the recipe solves,
 *                    who it's for, what makes it production-grade.
 * @param vertical    high-level segment tag the catalog uses to filter:
 *                    {@code education}, {@code finance}, {@code healthcare},
 *                    {@code general}. Mirrors §VIII.3's verticalization play.
 * @param tags        free-form keyword tags for in-catalog search (e.g.
 *                    {@code b2c}, {@code lead-capture}, {@code lgpd}).
 * @param flowCount   how many chat flows the install will create.
 * @param personaCount how many personas the install will attach to the agent
 *                    (existing personas matched by name are reused, not duplicated).
 * @param slotCount   how many typed slots the install will register on the agent.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurChatFlowRecipeSummaryDto(
        String id,
        String version,
        String name,
        String description,
        String vertical,
        List<String> tags,
        int flowCount,
        int personaCount,
        int slotCount) {
}
