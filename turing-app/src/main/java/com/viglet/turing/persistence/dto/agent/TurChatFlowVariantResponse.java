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
 * T97 / §VII.11.g — outcome of a variant-generation request.
 *
 * <p>The service does NOT persist the variant — the candidate is returned
 * so the author can review (and tweak) it in the editor before saving via
 * the standard {@code POST /chat-flow} endpoint.
 *
 * @param success         true when the LLM produced a usable rewrite.
 * @param error           human-readable error message when {@code success}
 *                        is false; {@code null} otherwise.
 * @param candidate       proposed variant (not persisted, {@code id == null})
 *                        when {@code success} is true; {@code null} otherwise.
 * @param summary         one-line natural-language description of the
 *                        rewrite the LLM applied. Shown in the dialog so the
 *                        author can confirm the variant matches the intent.
 * @param rewrittenNodes  ids of the nodes whose copy was actually changed.
 *                        Lets the UI highlight the diff at a glance.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurChatFlowVariantResponse(
        boolean success,
        String error,
        TurChatFlowDto candidate,
        String summary,
        List<String> rewrittenNodes) {
}
