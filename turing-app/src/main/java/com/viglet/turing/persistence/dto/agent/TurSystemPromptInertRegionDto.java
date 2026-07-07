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

/**
 * Block AK / T609 — one region of an agent segment that is <em>inert this
 * turn</em>: when a chat flow governs the turn, the flow addendum explicitly
 * orders the model to ignore the agent prompt's welcome / first-interaction /
 * routing scaffolding, so those tokens are attended-to dead weight. The Live
 * Preview dims the region and totals its token cost so the operator can see and
 * trim it.
 *
 * <p>Offsets are into the owning {@link TurSystemPromptSegmentDto#content()}
 * (the displayed text), so the frontend can slice and dim exactly that span.
 *
 * @param label  the section heading that names the inert region (e.g.
 *               "First interaction"), for the operator-facing summary.
 * @param start  inclusive char offset of the region in the segment content.
 * @param end    exclusive char offset of the region in the segment content.
 * @param tokens best-effort {@code chars/4} token cost of the inert region.
 * @param reason why it's inert: {@code "heuristic"} (a welcome/routing heading
 *               the flow neutralizes), {@code "concierge_only"} (author-marked
 *               concierge-only block), or {@code "flow_only"} (author-marked
 *               flow-only block, inert on a no-flow turn).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSystemPromptInertRegionDto(
        String label,
        int start,
        int end,
        int tokens,
        String reason) {
}
