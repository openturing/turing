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

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.TurChatFlowStaticPromptCache;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Two-call strategy: same prompt-level contract as
 * {@link HeuristicGuardrailStrategy} (so it shares the addendum), plus a
 * second LLM call after the chat reply that returns a small JSON verdict
 * judging whether the conversation stayed on track.
 *
 * <p>~200 extra tokens per turn while a flow is active. Falls back to the
 * heuristic strategy when the judge call fails or the JSON cannot be
 * parsed.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Slf4j
@Component
public class LlmJudgeGuardrailStrategy implements TurChatFlowGuardrailStrategy {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String NOT_SET = "(none)";

    /**
     * Per-node soft-fail policies for {@code aiQuestion} reads from
     * {@link ChatFlowNode#onJudgeReject()}. See the field's javadoc for the
     * mode semantics.
     *
     * @since 2026.2.31
     */
    private static final String REJECT_ADVANCE_WITH_LITERAL = "advance_with_literal";
    private static final String REJECT_REPROMPT = "reprompt";
    private static final String REJECT_BLOCK = "block";

    // §I.5 step 6 / T16 — deleted: RETRY_MIN_USER_MESSAGE_LENGTH constant
    // and the judgeRetryEnabled flag. The second-opinion retry block they
    // gated was removed entirely; onJudgeReject (#22) is the sole driver
    // of post-reject behavior. Recoverable from git log.

    private final HeuristicGuardrailStrategy fallback;
    private final TurChatFlowStaticPromptCache staticPromptCache;

    public LlmJudgeGuardrailStrategy(HeuristicGuardrailStrategy fallback,
            TurChatFlowStaticPromptCache staticPromptCache) {
        this.fallback = fallback;
        this.staticPromptCache = staticPromptCache;
    }

    @Override
    public TurChatFlowGuardrailMethod getMethod() {
        return TurChatFlowGuardrailMethod.LLM_JUDGE;
    }

    @Override
    public String buildSystemPromptAddendum(TurChatFlow flow, ChatFlowNode node,
            TurChatFlowState state, ChatFlowGraph graph) {
        // Layer 1 (the prompt-side contract) is shared with every other strategy
        // via the static-prompt cache; the judge only adds layer 2 inside `advance`.
        // T31 / §IV.5 — static portions served from the (flowId, nodeId)
        // cache; the "Already collected" line is built per turn.
        return staticPromptCache.buildAddendum(flow, node, state, graph, true);
    }

    @Override
    public String advance(AdvanceContext ctx) {
        ChatFlowNode node = ctx.currentNode();
        if ("end".equals(node.type())) {
            return null;
        }
        ChatModel judgeModel = ctx.auxiliaryModel();
        if (judgeModel == null) {
            log.warn("[LlmJudge] No model available — falling back to heuristic");
            return fallback.advance(ctx);
        }

        JudgeVerdict verdict = askJudge(judgeModel, node, ctx.userMessage(), ctx.assistantMessage());
        if (verdict == null) {
            log.warn("[LlmJudge] No usable verdict — falling back to heuristic");
            return fallback.advance(ctx);
        }

        // §I.5 step 6 / T16 — deleted: the second-opinion retry block
        // (patch #21). The behavior on a rejected verdict is now decided
        // SOLELY by ChatFlowNode.onJudgeReject (patch #22, shipped):
        //   • REJECT_ADVANCE_WITH_LITERAL → force-capture below
        //   • REJECT_BLOCK → stay on node
        //   • REJECT_REPROMPT (default) → stay with redirect_message
        // The isNonBlankRedirect heuristic is gone — author-declared
        // policy is unambiguous, no need to guess from redirect length.

        // Short-message force-capture path for {@code advance_with_literal}.
        //
        // The retry block above only fires for messages ≥
        // {@link #RETRY_MIN_USER_MESSAGE_LENGTH} chars (designed for natural-
        // language flakes, not short alphanumeric replies). When the judge
        // rejects a SHORT reply — coupon codes ("INSPER10"), short answers
        // ("CFO"), yes/no flavors — the retry never runs, and the soft-fail
        // policy never gets a chance to force-capture from inside that block.
        //
        // Honor {@code onJudgeReject=advance_with_literal} here, OUTSIDE the
        // length-gated retry: if the policy declares "advance with literal"
        // and the judge still ended up with no collected value on a slot-
        // collecting node, capture the user's message as the slot value.
        // {@code block} and {@code reprompt} keep the legacy behavior
        // (stay on node) so no node accidentally inherits the soft-fail.
        TurChatFlowState state = ctx.state();
        Map<String, String> variables = ChatFlowOps.readVariables(state);

        if (REJECT_ADVANCE_WITH_LITERAL.equals(resolveRejectPolicy(node))
                && !verdict.abandoned()
                && !verdict.readyToAdvance()
                && (verdict.collectedValue() == null || verdict.collectedValue().isBlank())
                && node.outputVariable() != null && !node.outputVariable().isBlank()
                && ctx.userMessage() != null && !ctx.userMessage().isBlank()) {
            // Defense-in-depth: when the slot is ALREADY populated (e.g. CV
            // extraction wrote `name` out-of-band while the cursor was parked
            // on `ai-name`) and the node didn't request override, the safe
            // thing is to advance WITHOUT overwriting the existing value.
            // Otherwise we'd clobber a good value (e.g. "Maria" from the CV)
            // with whatever short message the user happened to send (e.g.
            // "oi" — a no-op trigger from the front-end after CV upload).
            String existingValue = variables.get(node.outputVariable().trim());
            boolean slotAlreadyFilled = existingValue != null && !existingValue.isBlank();
            boolean overrideExisting = Boolean.TRUE.equals(node.overrideExistingValue());
            if (slotAlreadyFilled && !overrideExisting) {
                log.info("[LlmJudge] onJudgeReject=advance_with_literal on node '{}': slot '{}' "
                        + "already filled out-of-band with '{}' AND overrideExistingValue!=true "
                        + "— advancing without overwriting (user message '{}' dropped as no-op)",
                        node.id(), node.outputVariable(), existingValue, ctx.userMessage());
                verdict = new JudgeVerdict(verdict.onTopic(),
                        existingValue,
                        /* readyToAdvance */ true,
                        verdict.redirectMessage(),
                        /* abandoned */ false);
            } else {
                log.info("[LlmJudge] onJudgeReject=advance_with_literal — force-capturing "
                        + "short reply '{}' on node '{}' (judge rejected, retry path skipped due to length)",
                        ctx.userMessage(), node.id());
                verdict = new JudgeVerdict(verdict.onTopic(),
                        ctx.userMessage().trim(),
                        /* readyToAdvance */ true,
                        verdict.redirectMessage(),
                        /* abandoned */ false);
            }
        }
        log.info("[LlmJudge] Verdict on node '{}': onTopic={} ready={} abandoned={} collected='{}'",
                node.id(),
                verdict.onTopic(),
                verdict.readyToAdvance(),
                verdict.abandoned(),
                verdict.collectedValue());

        // Abandon → end gracefully and use the judge's farewell as the reply.
        if (verdict.abandoned()) {
            log.info("[LlmJudge] Abandon detected on node '{}' — ending flow", node.id());
            ChatFlowOps.writeVariables(state, variables);
            ChatFlowOps.transitionToEnd(state, ctx.graph());
            String farewell = verdict.redirectMessage();
            return (farewell != null && !farewell.isBlank()) ? farewell.trim() : null;
        }

        // ─── Capture + advance evaluation runs FIRST ─────────────────
        // The on_topic flag describes whether the BOT'S reply was on-goal.
        // It must NOT gate state transitions: when the user supplied a
        // valid value but the bot improvised an off-goal reply (e.g. a
        // premature wrap-up summary at ask-flavor), we still need to
        // capture the value and move to the next step. on_topic only
        // decides what TEXT we display back.
        boolean hasOutputVar = node.outputVariable() != null && !node.outputVariable().isBlank();
        String collected = verdict.collectedValue() == null ? null : verdict.collectedValue().trim();
        boolean hasCollected = collected != null && !collected.isBlank();

        // Defensive fallback: if the judge said advance but skipped extraction,
        // use the user's raw message rather than dropping a known-good turn.
        if (!hasCollected && hasOutputVar && verdict.readyToAdvance()
                && ctx.userMessage() != null && !ctx.userMessage().isBlank()) {
            collected = ctx.userMessage().trim();
            hasCollected = true;
            log.info("[LlmJudge] Judge said advance but skipped extraction — "
                    + "using user message '{}' as value for '{}'",
                    collected, node.outputVariable());
        }

        // Normalize yes/no tokens to lowercase for stable SpEL conditions.
        if (hasCollected) {
            collected = ChatFlowOps.normalizeYesNo(collected);
        }
        // T23 / §IV.6 — when the node carries inlineOptions, canonicalize
        // the captured value against them via Lucene LevenshteinAutomata
        // (exact → substring → edit distance 1 → edit distance 2). The
        // judge already canonicalizes most of the time, but diacritic
        // variants ("Gerente Senior" vs "Gerente Sênior") and prefix
        // noise ("Sr Comprador" vs "Comprador") slip through. Storing
        // the canonical label means downstream switch nodes / SpEL
        // conditions see the EXACT chip text the admin configured.
        if (hasCollected) {
            collected = ChatFlowOps.canonicalizeAgainstInlineOptions(collected, node.inlineOptions());
        }
        if (hasCollected && hasOutputVar) {
            variables.put(node.outputVariable().trim(), collected);
            log.info("[LlmJudge] Captured '{}' = '{}'", node.outputVariable(), collected);
        }

        // Re-check the validation rule so a single permissive verdict cannot
        // push the flow past a required field (e.g. "pizza" as a phone).
        boolean valid = !hasOutputVar
                || (hasCollected
                        && ChatFlowOps.valueMatchesRule(node.validationRule(), collected));

        // The judge's system prompt has an explicit STRICT RULE: "if you set
        // collected_value to a non-null string AND no validation rule fails
        // for it, you MUST set ready_to_advance to true". gpt-4o-mini
        // occasionally violates this — captures a perfectly valid value but
        // returns ready_to_advance=false (anecdotally because it disliked the
        // bot's reply and tried to "punish" by holding the flow). Enforce
        // the rule on our side: a captured + valid value advances the flow
        // regardless of what the verdict claimed. The on_topic flag still
        // controls the displayed text (judge redirect when off-topic, even
        // though we advance the state).
        boolean enforcedReady = verdict.readyToAdvance() || (hasCollected && valid);
        if (!verdict.readyToAdvance() && enforcedReady) {
            log.info("[LlmJudge] Judge violated its own STRICT RULE on node '{}' "
                    + "(collected='{}' valid=true but ready=false) — force-advancing",
                    node.id(), collected);
        }
        boolean shouldAdvance = enforcedReady && valid;

        if (!shouldAdvance) {
            // Not advancing: write current variables back and decide reply.
            ChatFlowOps.writeVariables(state, variables);
            log.info("[LlmJudge] Stay on node '{}' (advance={}, valid={}, onTopic={})",
                    node.id(), verdict.readyToAdvance(), valid, verdict.onTopic());
            // Bot reply was off-goal: replace with the judge's redirect when present.
            if (!verdict.onTopic()) {
                String redirect = verdict.redirectMessage();
                if (redirect != null && !redirect.isBlank()) {
                    log.info("[LlmJudge] Off-topic on node '{}' — using judge redirect", node.id());
                    return redirect.trim();
                }
                log.info("[LlmJudge] Off-topic on node '{}' — no redirect, keeping LLM reply",
                        node.id());
            }
            return null;
        }

        // ─── We ARE advancing ────────────────────────────────────────
        String previous = node.id();
        String next = ChatFlowOps.advanceToFirstEdge(state, ctx.graph(), node);
        if (!previous.equals(next)) {
            log.info("[LlmJudge] Advanced '{}' → '{}' on conversation '{}'",
                    previous, next, state.getConversationId());
        }
        ChatFlowOps.writeVariables(state, variables);
        // If the bot's reply was off-goal (e.g. it improvised a wrap-up
        // summary instead of asking the next question), the user would see a
        // misleading message even though we correctly advanced. Don't replace
        // with the judge's redirect — that targets the OLD goal which is now
        // satisfied. Logging is enough; the next turn will produce the right
        // question for the new node.
        if (!verdict.onTopic()) {
            log.info("[LlmJudge] Advanced past off-goal bot reply on node '{}' — "
                    + "next turn will land on the new node's goal", previous);
        }
        return null;
    }

    /**
     * Resolves the soft-fail policy declared on the node, defaulting to
     * {@link #REJECT_REPROMPT} when the field is null/blank or names an
     * unknown value. Lowercased so authors can write either
     * {@code "advance_with_literal"} or {@code "ADVANCE_WITH_LITERAL"} in
     * the flow JSON without surprise.
     *
     * @since 2026.2.31
     */
    static String resolveRejectPolicy(ChatFlowNode node) {
        String raw = node.onJudgeReject();
        if (raw == null || raw.isBlank()) {
            return REJECT_REPROMPT;
        }
        String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case REJECT_ADVANCE_WITH_LITERAL, REJECT_BLOCK, REJECT_REPROMPT -> normalized;
            default -> {
                log.warn("[LlmJudge] Unknown onJudgeReject='{}' on node '{}' — defaulting to '{}'",
                        raw, node.id(), REJECT_REPROMPT);
                yield REJECT_REPROMPT;
            }
        };
    }

    // §I.5 step 6 / T16 — deleted: shouldRetryVerdict + isNonBlankRedirect.
    // The retry block they served has been removed; reject policy is now
    // entirely declarative via ChatFlowNode.onJudgeReject (#22).

    // ─────────────────────────── Judge call ───────────────────────────

    /**
     * Single-shot judge call. Returns null when the call fails or the reply
     * cannot be parsed as the expected JSON shape.
     */
    private JudgeVerdict askJudge(ChatModel model,
            ChatFlowNode node,
            String userMessage,
            String assistantMessage) {
        String sys = """
                You are a strict conversation auditor. You receive the active step of a guided
                chat flow plus the most recent user/assistant exchange and decide five things:

                1. on_topic: did the ASSISTANT stay on the goal of the active step? This
                   judges the assistant's reply only — never gate the user's value on this.
                2. collected_value: did the *user's last message* supply whatever ANSWERS
                   the Goal? Be permissive about FORMAT — bare replies count, no need for
                   full sentences. Examples by goal type:
                     - Goal asks for a name → "Alexandre", "Alexandre Oliveira"
                     - Goal asks for an email → "foo@bar.com"
                     - Goal asks for a phone → "+5511999999999", "11 99999-9999"
                     - Goal asks for a date → "2026-04-28", "28/04/2026"
                     - Goal asks a yes/no question → "sim", "não", "yes", "no", "yep",
                       "nope" — these ARE values; do NOT return null for them just because
                       they are short.
                     - Goal asks for a flavor / option / category → the option name verbatim
                       ("portuguesa", "calabresa", "média", "grande", "pix", "dinheiro").
                       Variants with prepositions still count: "pizza de portuguesa",
                       "sabor portuguesa", "quero portuguesa" → all → "portuguesa".
                   POLITENESS MARKERS in isolation are NEVER values regardless of Goal:
                     "obrigado", "valeu", "ok", "thanks", "thank you", "got it".
                   CONVERSATIONAL INTENTS — phrases that say "I want to start X" but do
                   NOT answer the Goal — are NEVER values:
                     Goal: "ask for the user's name" + user: "quero me inscrever" → null
                     Goal: "ask for an email" + user: "olá" → null
                   When the user's message clearly answers the Goal, EXTRACT IT.
                3. ready_to_advance: should the conversation move to the next step?
                   STRICT RULE: if you set collected_value to a non-null string AND no
                   validation rule fails for it, you MUST set ready_to_advance to true.
                   on_topic does NOT matter for this decision — the user did the right
                   thing even if the assistant didn't. For steps without an output
                   variable, true when the goal is clearly addressed by the assistant.
                   NEVER true on the same turn where collected_value is null for a step
                   that requires a value.
                4. abandoned: true ONLY when the user clearly wants to quit, cancel, give up
                   on, or skip this flow ("desisto", "cancelar", "quero parar", "no thanks",
                   "I don't want to continue", etc.). When abandoned is true, the flow ends
                   and the conversation continues freely.
                5. redirect_message: written in the SAME LANGUAGE as the user's last message,
                   short and polite, addressed to the user. Use it for:
                   - on_topic=false (not abandoned): a one-sentence nudge back to the goal.
                     Do NOT quote the goal verbatim, do NOT mention internal terms like
                     "step", "goal" or "output variable".
                   - abandoned=true: a graceful goodbye that lets them go without pressure
                     (e.g. "Sem problema, qualquer coisa é só me chamar.").
                   Empty string in all other cases.

                Reply with EXACTLY one JSON object on a single line, no prose, no code fences,
                no explanations. Example:
                {"on_topic": true, "collected_value": "x@y.com", "ready_to_advance": true, "abandoned": false, "redirect_message": ""}

                Active step: %s
                Goal: %s
                Output variable to collect: %s
                Validation rule: %s
                """.formatted(
                ChatFlowOps.safe(node.label()),
                node.aiInstruction() == null ? NOT_SET : node.aiInstruction().trim(),
                node.outputVariable() == null || node.outputVariable().isBlank()
                        ? NOT_SET : node.outputVariable().trim(),
                node.validationRule() == null || node.validationRule().isBlank()
                        ? NOT_SET : node.validationRule().trim());

        String user = "User said: " + (userMessage == null ? "" : userMessage.trim()) + "\n"
                + "Assistant said: " + (assistantMessage == null ? "" : assistantMessage.trim());

        String reply;
        try {
            Prompt prompt = new Prompt(List.of(new SystemMessage(sys), new UserMessage(user)));
            var response = model.call(prompt);
            reply = response.getResult() != null
                    && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null
                            ? response.getResult().getOutput().getText().trim()
                            : "";
        } catch (Exception e) {
            log.warn("[LlmJudge] Judge call failed: {}", e.getMessage());
            return null;
        }
        if (reply.isEmpty()) {
            return null;
        }
        log.info("[LlmJudge] Raw verdict: '{}'", reply);
        return parseJudgeVerdict(reply);
    }

    private static JudgeVerdict parseJudgeVerdict(String reply) {
        String json = ChatFlowOps.stripFences(reply);
        int braceStart = json.indexOf('{');
        int braceEnd = json.lastIndexOf('}');
        if (braceStart < 0 || braceEnd <= braceStart) {
            return null;
        }
        json = json.substring(braceStart, braceEnd + 1);
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(json,
                    new TypeReference<Map<String, Object>>() {
                    });
            boolean onTopic = parsed.get("on_topic") instanceof Boolean b && b;
            String collected = parsed.get("collected_value") instanceof String s ? s : null;
            boolean ready = parsed.get("ready_to_advance") instanceof Boolean b && b;
            String redirect = parsed.get("redirect_message") instanceof String s ? s : null;
            boolean abandoned = parsed.get("abandoned") instanceof Boolean b && b;
            return new JudgeVerdict(onTopic, collected, ready, redirect, abandoned);
        } catch (JacksonException e) {
            log.warn("[LlmJudge] Could not parse JSON '{}': {}", json, e.getMessage());
            return null;
        }
    }

    /**
     * Defensively typed verdict — every field defaults conservatively when
     * absent. {@code redirectMessage} is only used when {@code onTopic} is
     * false or {@code abandoned} is true.
     */
    // Package-private so unit tests in the same package can construct
    // verdict instances directly (the canonical way to pin force-capture
    // heuristics without booting a ChatModel or reflecting into the class).
    record JudgeVerdict(boolean onTopic, String collectedValue, boolean readyToAdvance,
            String redirectMessage, boolean abandoned) {
    }
}
