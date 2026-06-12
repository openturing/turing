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
import com.viglet.turing.persistence.model.agent.TurChatFlowCaptureMode;
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

    /**
     * T51 / §VII.4.e — capture-first inversion. The confidence grade the
     * advisory judge writes into a parallel {@code <slot>__confidence} slot
     * (e.g. {@code email} → {@code email__confidence}). Downstream
     * switch/condition nodes can branch on it; a {@code low} grade also marks
     * a provisional capture as replaceable on the next re-prompt turn.
     *
     * @since 2026.3.1
     */
    static final String CONFIDENCE_SUFFIX = "__confidence";
    static final String CONFIDENCE_HIGH = "high";
    static final String CONFIDENCE_LOW = "low";

    /**
     * T53 / §VII.4.g — abandonment auto-escalation. When the flow opts in via
     * {@link TurChatFlow#getAbandonHandoffMessage()}, a judge-detected
     * abandonment writes this tracking slot (value {@link #HANDOFF_OFFERED_ABANDON})
     * so the chat UI can surface a handoff CTA and analytics can measure the
     * abandon → handoff conversion. Distinct from the {@code handoff_status}
     * slot written by {@code TurChatHandoffService} once the visitor actually
     * clicks through — this one records the system-initiated OFFER.
     *
     * @since 2026.3.1
     */
    static final String HANDOFF_OFFERED_SLOT = "handoff_offered";
    static final String HANDOFF_OFFERED_ABANDON = "abandon";

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
        // T51 / §VII.4.e — capture-first inversion. When the flow opts into
        // CAPTURE_THEN_GATE / CAPTURE_THEN_GRADE the slot is written BEFORE the
        // judge runs (the judge becomes advisory). VALIDATE_THEN_CAPTURE keeps
        // the legacy gate-then-capture body below untouched.
        TurChatFlowCaptureMode captureMode = resolveCaptureMode(ctx);
        if (captureMode != TurChatFlowCaptureMode.VALIDATE_THEN_CAPTURE) {
            return advanceCaptureFirst(ctx, captureMode);
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
        // rejects a SHORT reply — coupon codes ("ACME10"), short answers
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

        // Abandon → end gracefully. T53 may turn the goodbye into a handoff
        // offer (writes the handoff_offered slot into `variables`) when the
        // flow opts in; otherwise the judge's plain farewell is used.
        if (verdict.abandoned()) {
            log.info("[LlmJudge] Abandon detected on node '{}' — ending flow", node.id());
            String farewell = resolveAbandonReply(ctx, variables, verdict);
            ChatFlowOps.writeVariables(state, variables);
            ChatFlowOps.transitionToEnd(state, ctx.graph());
            return farewell;
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

    // ───────────────────── T51 capture-first inversion ─────────────────────

    /**
     * Reads the flow's {@link TurChatFlowCaptureMode}, defaulting to
     * {@link TurChatFlowCaptureMode#VALIDATE_THEN_CAPTURE} when no flow is
     * present (unit tests) or the column is null (pre-T51 rows that predate
     * the default-backfill).
     *
     * @since 2026.3.1
     */
    static TurChatFlowCaptureMode resolveCaptureMode(AdvanceContext ctx) {
        TurChatFlow flow = ctx.flow();
        if (flow == null || flow.getCaptureMode() == null) {
            return TurChatFlowCaptureMode.VALIDATE_THEN_CAPTURE;
        }
        return flow.getCaptureMode();
    }

    /**
     * Capture-then-grade turn. The user reply is persisted into the slot
     * BEFORE the judge runs, so a slot-collecting node never ends a turn with
     * {@code slot=null}. The judge is advisory: it refines the captured value
     * and writes a {@code <slot>__confidence} grade, but cannot block a value.
     *
     * <ul>
     *   <li>{@code CAPTURE_THEN_GATE} — a failing deterministic
     *       {@code validationRule} still parks the cursor for a re-prompt
     *       (now with the value captured + {@code confidence=low}).</li>
     *   <li>{@code CAPTURE_THEN_GRADE} — the flow always advances unless the
     *       user abandoned; validation + judge only set the grade.</li>
     * </ul>
     *
     * @since 2026.3.1
     */
    private String advanceCaptureFirst(AdvanceContext ctx, TurChatFlowCaptureMode mode) {
        ChatFlowNode node = ctx.currentNode();
        TurChatFlowState state = ctx.state();
        Map<String, String> variables = ChatFlowOps.readVariables(state);

        String slot = hasOutputVar(node) ? node.outputVariable().trim() : null;
        String userMsg = ctx.userMessage() == null ? "" : ctx.userMessage().trim();

        // No answer yet on a slot-collecting node — the question was just asked
        // and we're waiting. Nothing to capture or grade; stay put (mirrors the
        // legacy "collected==null → stay"). Guards CAPTURE_THEN_GRADE from
        // advancing past an unanswered question on a no-op trigger turn.
        if (slot != null && userMsg.isBlank()) {
            return null;
        }

        // Decide write-eligibility ONCE on the ORIGINAL slot state, before any
        // mutation — so both the provisional and the refined write share the
        // same "don't clobber a protected out-of-band value" rule.
        boolean mayWrite = slot != null && mayWriteSlot(node, slot, variables);

        // ── 1. Capture-first: provisional raw write ──
        if (mayWrite && !userMsg.isBlank()) {
            variables.put(slot, userMsg);
            log.info("[LlmJudge/captureFirst] Provisional capture '{}' = '{}' (pre-judge)", slot, userMsg);
        }

        // ── 2. Run the judge (advisory) ──
        JudgeVerdict verdict = runJudgeAdvisory(ctx);

        // Abandon still ends the flow — a user-driven exit, not the slot=null
        // stall T51 targets. Persist whatever was captured so far.
        if (verdict != null && verdict.abandoned()) {
            log.info("[LlmJudge/captureFirst] Abandon detected on node '{}' — ending flow", node.id());
            String farewell = resolveAbandonReply(ctx, variables, verdict);
            ChatFlowOps.writeVariables(state, variables);
            ChatFlowOps.transitionToEnd(state, ctx.graph());
            return farewell;
        }

        // ── 3. Refine: prefer the judge's cleaner extraction over the raw reply ──
        refineCapture(node, slot, mayWrite, verdict, variables);

        // ── 4. Grade confidence into the parallel slot ──
        String finalValue = slot == null ? null : variables.get(slot);
        boolean valid = slot == null
                || ChatFlowOps.valueMatchesRule(node.validationRule(), finalValue);
        boolean judgeEndorses = verdict != null && verdict.onTopic() && verdict.readyToAdvance();
        boolean highConfidence = valid && judgeEndorses;
        if (slot != null) {
            variables.put(slot + CONFIDENCE_SUFFIX, highConfidence ? CONFIDENCE_HIGH : CONFIDENCE_LOW);
        }

        // ── 5. Advance decision ──
        if (!decideCaptureFirstAdvance(slot != null, mode, valid, verdict)) {
            ChatFlowOps.writeVariables(state, variables);
            log.info("[LlmJudge/captureFirst] GATE stay on '{}' (value='{}', confidence=low) — re-prompt",
                    node.id(), finalValue);
            return offTopicRedirect(verdict);
        }
        String previous = node.id();
        String next = ChatFlowOps.advanceToFirstEdge(state, ctx.graph(), node);
        ChatFlowOps.writeVariables(state, variables);
        if (!previous.equals(next)) {
            log.info("[LlmJudge/captureFirst] Advanced '{}' → '{}' (mode={}, confidence={})",
                    previous, next, mode, highConfidence ? CONFIDENCE_HIGH : CONFIDENCE_LOW);
        }
        // Advanced regardless of on_topic: the judge's redirect targets the OLD
        // goal which is now satisfied, so don't surface it — the next turn
        // produces the right prompt for the new node.
        return null;
    }

    private static boolean hasOutputVar(ChatFlowNode node) {
        return node.outputVariable() != null && !node.outputVariable().isBlank();
    }

    /**
     * Write-eligibility for capture-first, evaluated on the ORIGINAL slot
     * state. Writable when the slot is empty, the node opts into override, or
     * the existing value carries a {@code low} confidence grade — the last
     * case lets a re-prompt answer replace our own provisional capture from a
     * prior failed attempt on the same node, while still protecting a genuine
     * out-of-band high-confidence value (e.g. a CV-extracted name).
     */
    private static boolean mayWriteSlot(ChatFlowNode node, String slot,
            Map<String, String> originalVariables) {
        String existing = originalVariables.get(slot);
        if (existing == null || existing.isBlank()) {
            return true;
        }
        if (Boolean.TRUE.equals(node.overrideExistingValue())) {
            return true;
        }
        return CONFIDENCE_LOW.equals(originalVariables.get(slot + CONFIDENCE_SUFFIX));
    }

    /** Overwrites the provisional capture with the judge's normalized/canonicalized value. */
    private static void refineCapture(ChatFlowNode node, String slot, boolean mayWrite,
            JudgeVerdict verdict, Map<String, String> variables) {
        if (slot == null || !mayWrite || verdict == null) {
            return;
        }
        String collected = verdict.collectedValue() == null ? null : verdict.collectedValue().trim();
        if (collected == null || collected.isBlank()) {
            return;
        }
        collected = ChatFlowOps.normalizeYesNo(collected);
        collected = ChatFlowOps.canonicalizeAgainstInlineOptions(collected, node.inlineOptions());
        variables.put(slot, collected);
        log.info("[LlmJudge/captureFirst] Refined capture '{}' = '{}' (judge extraction)", slot, collected);
    }

    private JudgeVerdict runJudgeAdvisory(AdvanceContext ctx) {
        ChatModel model = ctx.auxiliaryModel();
        if (model == null) {
            log.warn("[LlmJudge/captureFirst] No model available — capturing without a grade");
            return null;
        }
        return askJudge(model, ctx.currentNode(), ctx.userMessage(), ctx.assistantMessage());
    }

    /**
     * Capture-first advance rule. Informational nodes (no output var) keep the
     * legacy "advance when the judge says the goal is addressed" (and never
     * stall when no judge is available). For slot-collecting nodes,
     * {@code CAPTURE_THEN_GRADE} always advances; {@code CAPTURE_THEN_GATE}
     * advances only when the deterministic {@code validationRule} passes.
     */
    private static boolean decideCaptureFirstAdvance(boolean hasOutputVar,
            TurChatFlowCaptureMode mode, boolean valid, JudgeVerdict verdict) {
        if (!hasOutputVar) {
            return verdict == null || verdict.readyToAdvance();
        }
        if (mode == TurChatFlowCaptureMode.CAPTURE_THEN_GRADE) {
            return true;
        }
        return valid;
    }

    /**
     * T53 / §VII.4.g — resolves the reply shown when the judge detects
     * abandonment, shared by the legacy and capture-first paths.
     *
     * <p>When the flow opts in (non-blank {@link
     * TurChatFlow#getAbandonHandoffMessage()}), writes the {@link
     * #HANDOFF_OFFERED_SLOT} tracking slot into {@code variables} (so the SSE
     * channel + analytics see the offer and the UI can render a handoff CTA)
     * and returns the configured offer text instead of just closing. Otherwise
     * returns the judge's plain farewell ({@code null} when blank) — the legacy
     * "say goodbye and close" behaviour.
     *
     * @since 2026.3.1
     */
    private static String resolveAbandonReply(AdvanceContext ctx,
            Map<String, String> variables, JudgeVerdict verdict) {
        String judgeFarewell = verdict.redirectMessage();
        judgeFarewell = (judgeFarewell != null && !judgeFarewell.isBlank())
                ? judgeFarewell.trim()
                : null;

        TurChatFlow flow = ctx.flow();
        String offer = flow == null ? null : flow.getAbandonHandoffMessage();
        if (offer == null || offer.isBlank()) {
            return judgeFarewell;
        }

        variables.put(HANDOFF_OFFERED_SLOT, HANDOFF_OFFERED_ABANDON);
        String conversationId = ctx.state() == null ? "?" : ctx.state().getConversationId();
        log.info("[LlmJudge] Abandonment handoff offered (conv={}) — escalating to a human "
                + "consultant instead of closing", conversationId);
        return offer.trim();
    }

    private static String offTopicRedirect(JudgeVerdict verdict) {
        if (verdict != null && !verdict.onTopic()) {
            String redirect = verdict.redirectMessage();
            if (redirect != null && !redirect.isBlank()) {
                return redirect.trim();
            }
        }
        return null;
    }

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

                Worked examples — study these. They cover the three judgment calls that
                smaller models most often get wrong; copy the same reasoning, not the
                literal values. Each shows the relevant inputs followed by the one
                correct JSON line.

                — Example A — compound answer: capture the slot, ignore the extra —
                Goal: ask for the visitor's job title (cargo)
                Output variable to collect: cargo
                User said: sou gerente de compras há uns 5 anos
                Assistant said: Perfeito! E qual é o seu principal objetivo?
                {"on_topic": true, "collected_value": "Gerente de Compras", "ready_to_advance": true, "abandoned": false, "redirect_message": ""}
                Why: the message carries BOTH a job title and a tenure. Extract only what
                the Goal asks for (the cargo) and drop the "5 anos" — a compound reply is
                still a valid answer.

                — Example B — informal phrasing still answers the Goal —
                Goal: ask what the visitor wants to achieve (objetivo)
                Output variable to collect: objetivo
                User said: ah sei lá, queria vender mais, sabe? aumentar as vendas
                Assistant said: Ótimo, vou te ajudar com isso.
                {"on_topic": true, "collected_value": "aumentar as vendas", "ready_to_advance": true, "abandoned": false, "redirect_message": ""}
                Why: hedging and filler ("ah sei lá", "sabe?") are NOT politeness markers —
                the sentence DOES answer the Goal. Capture the intent, not the filler, and
                advance.

                — Example C — stale option click routed to the wrong step: reject + redirect —
                Goal: ask for the visitor's email
                Output variable to collect: email
                User said: portuguesa
                Assistant said: Boa escolha de sabor!
                {"on_topic": false, "collected_value": null, "ready_to_advance": false, "abandoned": false, "redirect_message": "Só preciso do seu e-mail para continuar — pode me passar?"}
                Why: "portuguesa" is a leftover option click from an earlier step; it does
                NOT answer the email Goal, so collected_value stays null and the flow must
                not advance. The assistant also drifted, so on_topic is false and a one-line
                redirect brings the user back to the goal.

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
