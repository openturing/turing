package com.viglet.turing.genai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.viglet.turing.persistence.model.agent.TurAgentChatMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * T1004 — the CALL/STREAM/AUTO branch decision.
 *
 * <p>Site-scoped chat emitted its whole answer as one SSE {@code content} event,
 * because an agent is {@link TurAgentChatMode#CALL} unless an operator changes
 * it, and {@link TurAgentChatMode#STREAM} would have traded away the tools on
 * every other surface that agent serves. {@link TurAgentChatMode#AUTO} decides
 * per turn instead, and these are the cases that decision has to get right.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurChatStreamingDispatcherModeTest {

    @ParameterizedTest(name = "{0}: flow={1} tools={2} -> stream={3}")
    @CsvSource({
            // CALL never streams, whatever the turn looks like.
            "CALL,   false, false, false",
            "CALL,   true,  false, false",
            "CALL,   false, true,  false",
            "CALL,   true,  true,  false",
            // STREAM keeps its opted-into semantics: a flow stops it, tools do not.
            "STREAM, false, false, true",
            "STREAM, false, true,  true",
            "STREAM, true,  false, false",
            "STREAM, true,  true,  false",
            // AUTO streams only when neither trade-off applies.
            "AUTO,   false, false, true",
            "AUTO,   false, true,  false",
            "AUTO,   true,  false, false",
            "AUTO,   true,  true,  false",
    })
    @DisplayName("the branch is chosen from the mode, the flow and the turn's toolset")
    void decidesTheBranch(TurAgentChatMode mode, boolean hasFlow, boolean toolsInPlay,
            boolean expected) {
        assertTrue(expected == TurChatStreamingDispatcher.shouldStream(mode, hasFlow, toolsInPlay),
                () -> "mode=" + mode + " hasFlow=" + hasFlow + " toolsInPlay=" + toolsInPlay
                        + " should " + (expected ? "" : "not ") + "stream");
    }

    @Test
    @DisplayName("the public site-chat turn streams under AUTO — no flow, and T647 stripped its tools")
    void publicSiteChatTurnStreamsUnderAuto() {
        // This is the turn T1004 measured: an anonymous visitor asking a RAG
        // question. There is no flow, and the untrusted-caller strip left the
        // callback array empty even on an agent that carries tools — so there is
        // nothing for the streaming branch to give up.
        assertTrue(TurChatStreamingDispatcher.shouldStream(TurAgentChatMode.AUTO, false, false));
    }

    @Test
    @DisplayName("the same agent's admin turn keeps its tools, because AUTO calls when tools are in play")
    void adminTurnWithToolsStillCalls() {
        // The point of deciding per turn rather than per agent: one agent serves
        // both surfaces, and flipping it to STREAM would have dropped the tools
        // here to gain streaming there.
        assertFalse(TurChatStreamingDispatcher.shouldStream(TurAgentChatMode.AUTO, false, true));
    }

    @Test
    @DisplayName("a flow-driven turn never streams, in any mode")
    void flowDrivenTurnNeverStreams() {
        // A streamed bubble cannot be un-emitted, and the post-LLM advance/regen
        // pipeline decides whether to rewrite it only after the whole text exists.
        for (TurAgentChatMode mode : TurAgentChatMode.values()) {
            assertFalse(TurChatStreamingDispatcher.shouldStream(mode, true, false),
                    () -> mode + " streamed a flow-driven turn");
            assertFalse(TurChatStreamingDispatcher.shouldStream(mode, true, true),
                    () -> mode + " streamed a flow-driven turn carrying tools");
        }
    }
}
