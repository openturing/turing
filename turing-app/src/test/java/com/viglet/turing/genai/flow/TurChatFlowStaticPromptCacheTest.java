/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.flow.ChatFlowNode.NodeData;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

/**
 * Tests for T31 / §IV.5 flow static-prompt cache. The unit tests below
 * pin the orchestration of head/tail/dynamic-variables concatenation and
 * the bypass behavior on null / terminal nodes; the {@code @Cacheable}
 * memoization itself is exercised at the Spring level by the existing
 * smoke ITs that boot the app context.
 *
 * <p>The self-injection used by the cache bean to route intra-class calls
 * through Spring's proxy is mimicked here by constructing two bean
 * instances: an "inner" with {@code self=null} that is never invoked
 * (the @Cacheable atoms don't read {@code self} on their own bodies),
 * and an "outer" whose {@code self} points at the inner. Calling
 * {@code outer.buildAddendum(...)} then routes through
 * {@code inner.staticHead(...)} which executes the underlying logic
 * without the caching advice (since @Cacheable is a no-op outside a
 * Spring context). This is exactly the path we want to verify
 * functionally — the cache layer is verified by separate ITs.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatFlowStaticPromptCacheTest {

    private TurChatFlowStaticPromptCache cache;
    private TurChatFlow flow;
    private ChatFlowGraph graph;

    @BeforeEach
    void setUp() {
        TurChatFlowStaticPromptCache inner = new TurChatFlowStaticPromptCache(null);
        cache = new TurChatFlowStaticPromptCache(inner);

        flow = new TurChatFlow();
        flow.setId("flow-test-1");
        flow.setName("Test Flow");

        // Empty graph (no edges) — we don't rely on next-step preview in
        // these unit tests; the few that do use a richer graph build it
        // explicitly.
        graph = new ChatFlowGraph(Collections.emptyList(), Collections.emptyList());
    }

    // ─────────────── buildAddendum orchestrator ───────────────

    @Test
    void buildAddendum_returnsEmptyForNullNode() {
        assertThat(cache.buildAddendum(flow, null, blankState(), graph, true)).isEmpty();
    }

    @Test
    void buildAddendum_returnsEmptyForTerminalNode() {
        ChatFlowNode endNode = node("end-id", "end", "End", null, null, null);
        assertThat(cache.buildAddendum(flow, endNode, blankState(), graph, true)).isEmpty();
    }

    @Test
    void buildAddendum_concatenatesHeadVariablesAndTail() {
        ChatFlowNode q = node("q-id", "aiQuestion", "Ask for name",
                "What is your name?", "userName", null);
        TurChatFlowState state = stateWithVariables("{\"company\":\"Viglet\"}");

        String out = cache.buildAddendum(flow, q, state, graph, true);

        // Head pieces: ACTIVE FLOW STEP, Goal, Collect
        // Dynamic line from state; Tail: HARD_RULES (one of its anchor phrases)
        assertThat(out)
                .contains("## ACTIVE FLOW STEP")
                // The node label is intentionally NOT emitted (pure token cost).
                .doesNotContain("### Ask for name")
                .contains("Goal: What is your name?")
                .contains("Collect: userName")
                .contains("Already collected: {company=Viglet}")
                .contains("Hard rules");
    }

    @Test
    void buildAddendum_omitsVariablesLineWhenStateBlank() {
        ChatFlowNode q = node("q-id", "aiQuestion", "Ask",
                "What is your name?", null, null);
        String out = cache.buildAddendum(flow, q, blankState(), graph, true);
        assertThat(out)
                .doesNotContain("Already collected:")
                .contains("Goal: What is your name?")
                .contains("Hard rules");
    }

    @Test
    void buildAddendum_nullFlowFallsBackToBlankFlowIdKey() {
        // Defensive — when no flow context is set up the call chain
        // shouldn't NPE; the addendum can still be built (just not cached).
        ChatFlowNode q = node("q-id", "aiQuestion", "Ask",
                "Goal text", null, null);
        String out = cache.buildAddendum(null, q, blankState(), graph, true);
        assertThat(out)
                .contains("Goal: Goal text")
                .contains("Hard rules");
    }

    // ─────────────── staticHead / staticTail content ───────────────

    @Test
    void staticHead_includesGoalCollectValidate() {
        ChatFlowNode q = node("q-id", "aiQuestion", "Capture email",
                "Ask for the email", "email", "email");
        String head = cache.staticHead(flow.getId(), q.id(), q);
        // Static head should NOT contain HARD_RULES (that's in the tail) nor
        // the variables line (that's dynamic and assembled by buildAddendum).
        // The node label is intentionally NOT emitted (pure token cost).
        assertThat(head)
                .contains("## ACTIVE FLOW STEP")
                .doesNotContain("### Capture email")
                .contains("Goal: Ask for the email")
                .contains("Collect: email")
                .contains("Validate the collected value as: email")
                .doesNotContain("Hard rules")
                .doesNotContain("Already collected:");
    }

    @Test
    void staticHead_emptyForTerminalNode() {
        ChatFlowNode endNode = node("end-id", "end", "End", null, null, null);
        assertThat(cache.staticHead(flow.getId(), endNode.id(), endNode)).isEmpty();
    }

    @Test
    void staticTail_includesHardRules() {
        ChatFlowNode q = node("q-id", "aiQuestion", "Ask", "Goal", null, null);
        String tail = cache.staticTail(flow.getId(), q.id(), true, q, graph);
        assertThat(tail)
                .contains("Hard rules")
                .doesNotContain("## ACTIVE FLOW STEP")
                .doesNotContain("Already collected:");
    }

    @Test
    void staticTail_omitsNextStepHintWhenIncludeIsFalse() {
        // We can't easily build a real next-step hint here without a
        // bigger graph; the assertion focuses on the toggle. Note: the
        // HARD_RULES text itself mentions {@code "Next step preview"} in
        // quotes, so we assert against the actual hint header which is
        // {@code "Next step preview (...)"} — the parenthesis is the
        // discriminator vs the quoted reference inside the rules block.
        ChatFlowNode q = node("q-id", "aiQuestion", "Ask", "Goal", null, null);
        String tail = cache.staticTail(flow.getId(), q.id(), false, q, graph);
        assertThat(tail)
                .doesNotContain("Next step preview (")
                .contains("Hard rules");
    }

    // ─────────────── ChatFlowOps split helpers ───────────────

    @Test
    void chatFlowOps_buildVariablesLine_emptyWhenStateBlank() {
        assertThat(com.viglet.turing.genai.flow.strategy.ChatFlowOps.buildVariablesLine(blankState()))
                .isEmpty();
    }

    @Test
    void chatFlowOps_buildVariablesLine_rendersCollectedSlots() {
        TurChatFlowState state = stateWithVariables("{\"name\":\"Marina\",\"role\":\"CFO\"}");
        String line = com.viglet.turing.genai.flow.strategy.ChatFlowOps.buildVariablesLine(state);
        assertThat(line)
                .startsWith("Already collected:")
                .contains("name=Marina")
                .contains("role=CFO")
                .endsWith("\n");
    }

    @Test
    void chatFlowOps_buildBaseAddendum_equalsHeadPlusVarsPlusTail() {
        // The static delegator must produce the same string as the three
        // split helpers concatenated — pins the "single source of truth"
        // claim in the helpers' javadoc.
        ChatFlowNode q = node("q-id", "aiQuestion", "Ask",
                "What's the phone?", "phone", "phone");
        TurChatFlowState state = stateWithVariables("{\"name\":\"Joao\"}");

        String composed = com.viglet.turing.genai.flow.strategy.ChatFlowOps.buildBaseAddendum(
                q, state, graph, true);
        String fromParts =
                com.viglet.turing.genai.flow.strategy.ChatFlowOps.buildStaticAddendumHead(q)
                        + com.viglet.turing.genai.flow.strategy.ChatFlowOps.buildVariablesLine(state)
                        + com.viglet.turing.genai.flow.strategy.ChatFlowOps.buildStaticAddendumTail(
                                q, graph, true);

        assertThat(composed).isEqualTo(fromParts);
    }

    // ─────────────── helpers ───────────────

    private static ChatFlowNode node(String id, String type, String label,
                                     String aiInstruction, String outputVariable,
                                     String validationRule) {
        return new ChatFlowNode(id, type, new NodeData(
                label, type, aiInstruction, outputVariable, validationRule,
                null, null, null, null, null, null, null, null,
                List.of(), List.of(), null, null, null, null, null, null, null, null, null));
    }

    private static TurChatFlowState blankState() {
        TurChatFlowState s = new TurChatFlowState();
        s.setVariablesJson(null);
        return s;
    }

    private static TurChatFlowState stateWithVariables(String json) {
        TurChatFlowState s = new TurChatFlowState();
        s.setVariablesJson(json);
        return s;
    }
}
