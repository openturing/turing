/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow.strategy;

import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

/**
 * One way of enforcing a chat flow at runtime. Each value of
 * {@link TurChatFlowGuardrailMethod} maps to exactly one Spring bean
 * implementing this interface; the engine looks them up by
 * {@link #getMethod()} and dispatches.
 *
 * <p>Adding a new methodology means dropping a new {@code @Component} that
 * implements this interface — no engine changes required.
 *
 * <p>Implementations <strong>mutate</strong> the runtime state in place
 * ({@code setCurrentNodeId}, {@code setVariablesJson}). They never persist:
 * the engine owns that side-effect, and uses the {@code currentNodeId}
 * delta before/after the strategy call to detect terminal transitions and
 * emit submission rows.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
public interface TurChatFlowGuardrailStrategy {

    /**
     * @return the enum value this strategy handles. The engine builds a
     *         {@code Map<TurChatFlowGuardrailMethod, …>} keyed on this
     *         value to dispatch.
     */
    TurChatFlowGuardrailMethod getMethod();

    /**
     * Builds the system-prompt addendum the chat LLM receives for the
     * current node. Returns {@code ""} when the current node is terminal,
     * so the conversation flows freely after the flow ends.
     * <p>
     * The {@code graph} is used to look ahead at the next interactive node
     * so the addendum can tell the chat LLM to chain into it (e.g. "after
     * capturing the name, immediately ask for the phone") — without that
     * hint, the bot stops after each value and the user has to send a
     * trigger message between every step.
     * <p>
     * The {@code flow} reference (T31 / §IV.5) is required so the static
     * portions of the addendum can be cached by {@code (flowId, nodeId)}
     * via {@link com.viglet.turing.genai.flow.TurChatFlowStaticPromptCache}.
     * Strategy implementations route their static-portion calls through
     * the cache bean; only the per-turn "Already collected" line is
     * built inline.
     */
    String buildSystemPromptAddendum(TurChatFlow flow, ChatFlowNode node,
            TurChatFlowState state, ChatFlowGraph graph);

    /**
     * Decides the next node and an optional assistant override given the
     * latest exchange. Mutates {@link AdvanceContext#state()} in place.
     *
     * @return the assistant message override, or {@code null} when the LLM
     *         reply should pass through unchanged. Only LLM_JUDGE and
     *         STRUCTURED_OUTPUT typically set this.
     */
    String advance(AdvanceContext ctx);
}
