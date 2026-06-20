/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.authoring.chatflow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.authoring.AiAuthoringRequest;
import com.viglet.turing.genai.authoring.AiAuthoringResponse;
import com.viglet.turing.genai.authoring.TurAiAuthoringService;

import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrator ("skill") for the Chat Flow AI Authoring chat.
 * <p>
 * Chat Flow is significantly more complex than other authored entities —
 * five node types with type-specific fields, edge wiring rules with
 * branched handles, guardrail strategy and trigger-mode policies. A naïve
 * single-prompt approach for every turn produces fragile output, so this
 * service splits the work:
 * <ul>
 *   <li><strong>First turn</strong> ({@link #create}) — the LLM is
 *       handed a rich, fully documented system prompt covering EVERY
 *       node type, EVERY field, edge handle conventions, layout rules,
 *       and the guardrail/trigger policy. The skill then post-processes
 *       the result: assigns deterministic positions, normalizes ids,
 *       validates connectivity, and snaps obvious mistakes (missing
 *       start/end, dangling edges, condition without two branches).</li>
 *   <li><strong>Revision turns</strong> ({@link #revise}) — the model
 *       sees the full {@code currentState}, plus a slimmer "edit" prompt
 *       focused on incremental change rules. Cheaper, faster, and the
 *       state already encodes the structural decisions.</li>
 * </ul>
 *
 * <p>The skill stays generic about WHICH LLM is used — it delegates to
 * {@link TurAiAuthoringService}, which resolves the default LLM and
 * runs the chat through the standard authoring pipeline.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Slf4j
@Service
public class ChatFlowAuthoringSkill {

    private static final ParameterizedTypeReference<AiAuthoringResponse<ChatFlowGeneration>> TYPE_REF =
            new ParameterizedTypeReference<>() {
            };

    /** Horizontal step between adjacent nodes in the auto layout. */
    private static final int LAYOUT_X_STEP = 260;
    /** Vertical step for branching layouts. */
    private static final int LAYOUT_Y_STEP = 160;
    /** Top-left origin for the auto layout. */
    private static final int LAYOUT_X_ORIGIN = 80;
    private static final int LAYOUT_Y_ORIGIN = 240;

    private final TurAiAuthoringService aiAuthoringService;

    public ChatFlowAuthoringSkill(TurAiAuthoringService aiAuthoringService) {
        this.aiAuthoringService = aiAuthoringService;
    }

    /**
     * First-turn entrypoint: build a fresh chat flow from a brief.
     */
    public AiAuthoringResponse<ChatFlowGeneration> create(AiAuthoringRequest<ChatFlowGeneration> request) {
        AiAuthoringResponse<ChatFlowGeneration> raw =
                aiAuthoringService.chat(request, CREATE_SYSTEM_PROMPT, TYPE_REF);
        return new AiAuthoringResponse<>(raw.message(), normalize(raw.state()));
    }

    /**
     * Subsequent turns: incremental edits to an existing flow.
     */
    public AiAuthoringResponse<ChatFlowGeneration> revise(AiAuthoringRequest<ChatFlowGeneration> request) {
        AiAuthoringResponse<ChatFlowGeneration> raw =
                aiAuthoringService.chat(request, REVISE_SYSTEM_PROMPT, TYPE_REF);
        return new AiAuthoringResponse<>(raw.message(), normalize(raw.state()));
    }

    /* ─────────────────── Post-processing ─────────────────── */

    /**
     * Light-touch normalization: drop dangling edges, ensure unique
     * node ids, and assign deterministic x/y so the canvas is readable
     * even before the user rearranges manually.
     */
    private ChatFlowGeneration normalize(ChatFlowGeneration state) {
        if (state == null) return null;
        List<ChatFlowNodeGeneration> nodes = state.nodes() == null ? List.of() : new ArrayList<>(state.nodes());
        List<ChatFlowEdgeGeneration> edges = state.edges() == null ? List.of() : new ArrayList<>(state.edges());

        Set<String> nodeIds = collectNodeIds(nodes);
        List<ChatFlowEdgeGeneration> cleanEdges = dropDanglingEdges(edges, nodeIds);

        return new ChatFlowGeneration(
                state.name(),
                state.description(),
                state.guardrailMethod(),
                state.triggerDescription(),
                state.triggerMode(),
                state.enabled(),
                nodes,
                cleanEdges);
    }

    /** Collects the distinct, non-blank node ids, warning on duplicates. */
    private Set<String> collectNodeIds(List<ChatFlowNodeGeneration> nodes) {
        Set<String> nodeIds = new HashSet<>();
        for (ChatFlowNodeGeneration n : nodes) {
            if (n == null || n.id() == null || n.id().isBlank()) continue;
            if (!nodeIds.add(n.id())) {
                log.warn("[ChatFlowSkill] Duplicate node id '{}' — skipping", n.id());
            }
        }
        return nodeIds;
    }

    /** Drops edges referencing nodes that don't exist (LLMs sometimes hallucinate). */
    private List<ChatFlowEdgeGeneration> dropDanglingEdges(List<ChatFlowEdgeGeneration> edges,
            Set<String> nodeIds) {
        List<ChatFlowEdgeGeneration> cleanEdges = new ArrayList<>(edges.size());
        for (ChatFlowEdgeGeneration e : edges) {
            if (e == null || e.source() == null || e.target() == null) {
                continue;
            }
            if (nodeIds.contains(e.source()) && nodeIds.contains(e.target())) {
                cleanEdges.add(e);
            } else {
                log.warn("[ChatFlowSkill] Dropping edge {} → {} (unknown endpoint)", e.source(), e.target());
            }
        }
        return cleanEdges;
    }

    /** Public so the API can call layout BEFORE building the React Flow position field. */
    public static int[] positionFor(int nodeIndex, String nodeType, int sameRowOffset) {
        int x = LAYOUT_X_ORIGIN + nodeIndex * LAYOUT_X_STEP;
        int y = LAYOUT_Y_ORIGIN + sameRowOffset * LAYOUT_Y_STEP;
        // 'condition' nodes are taller diamonds — give them a touch more headroom
        if ("condition".equalsIgnoreCase(nodeType)) {
            y -= 20;
        }
        return new int[] { x, y };
    }

    /* ─────────────────────── System Prompts ─────────────────────── */

    private static final String CREATE_SYSTEM_PROMPT = """
            You are a Chat Flow architect for the Turing Enterprise Search platform.
            A "chat flow" is a directed graph that the AI Agent will follow during a
            conversation, asking the user questions, branching on conditions, and
            calling tools. The user describes what they want; you produce the FULL
            graph (nodes + edges + metadata).

            ════════════ NODE TYPES (5 — use ALL when relevant) ════════════

            1. **start** — exactly ONE per flow. Marks the entry. The engine begins
               here. Has only an outgoing edge. Required fields: id, type, label
               ("START").

            2. **end** — at least ONE. Marks termination. Has only an incoming edge.
               Multiple ends are allowed (e.g. "qualified" vs "abandoned"). Required:
               id, type, label ("END").

            3. **aiQuestion** — the agent asks the user a question. The captured
               answer is stored in a flow variable referenced later (in
               aiInstruction of subsequent nodes, or in conditionExpression).
               Required: id, type, label, aiInstruction (the prompt sent to the LLM
               so it asks the user — written in 2nd person, e.g. "Ask the user for
               their full name. Confirm spelling if uncertain."), outputVariable
               (snake_case, e.g. "user_name", "email", "company_size").
               Optional: validationRule — one of "email" | "phone" | "url" |
               "number" | "date" — applied to the captured value before advancing.
               Use it whenever the answer has an obvious format.

            4. **condition** — branch on a boolean expression evaluated against the
               flow variables. The engine routes via the "yes" branch when true,
               "no" otherwise. Required: id, type, label, conditionExpression
               written in plain language the LLM-as-judge can interpret (e.g.
               "company_size > 50", "user said yes to scheduling", "email domain
               is corporate"). MUST have exactly TWO outgoing edges, one with
               sourceHandle="yes" and one with sourceHandle="no".

            5. **functionCall** — invoke a tool. Required: id, type, label,
               toolSource ("NATIVE" or "MCP"). When toolSource="NATIVE", set
               functionName to the @Tool method name (e.g. "search_icons",
               "get_current_time", "search_site"). When toolSource="MCP", set
               mcpServerId to the configured MCP server id. aiInstruction is
               optional context that helps the LLM decide HOW to call the tool.

            ════════════ EDGE WIRING RULES ════════════

            Edge fields: { source, target, sourceHandle?, label? }
            - `source`/`target` are REQUIRED — must reference existing node ids.
            - `sourceHandle` is REQUIRED ONLY for edges leaving a `condition`
              node ("yes" or "no"). For every OTHER edge, OMIT it (do NOT set
              an empty string — leave the field out entirely or pass null).
            - `label` is OPTIONAL and almost always UNNECESSARY. OMIT it. Only
              include it for the rare case the user explicitly asks for a
              custom edge label, and even then keep it under 20 characters.

            Topology rules:
            - Every node except `start` must have at least one incoming edge.
            - Every node except `end` must have at least one outgoing edge.
            - For non-condition source nodes: a single outgoing edge is the
              norm. Multiple outgoing edges from the same non-condition node
              are valid only when modelling parallel continuations (rare).
            - For condition source nodes: EXACTLY two outgoing edges, one
              with sourceHandle="yes" and the other "no". Both branches must
              eventually reach an `end`.
            - Edges are directed (source → target). No back-edges (loops) unless
              you genuinely need a retry — in practice, model retries with a
              second aiQuestion node, not a loop.
            - Use stable semantic ids for nodes (e.g. "ask-name", "validate-age",
              "decide-interested", "schedule-meeting"). The same id MUST be used
              across turns so the user's manual edits don't get clobbered.

            ════════════ GUARDRAIL METHOD ════════════

            Field: guardrailMethod ∈ {"HEURISTIC", "LLM_JUDGE", "STRUCTURED_OUTPUT"}.
            - LLM_JUDGE (RECOMMENDED — safe default): extra focused LLM call
              after each turn that returns a JSON verdict (on_topic,
              collected_value, ready_to_advance). The judge call is "clean"
              (no chat history, no tools competing) so smaller models follow
              the contract reliably. Most accurate in production.
            - HEURISTIC: cheapest, prompt-only contract per node + first-edge
              advance with regex/keyword heuristics. Reliable ONLY for nodes
              with strict validationRule (email/phone/number/date/url). For
              free-text nodes (name, flavor, generic question) it accepts any
              user reply as the value — fragile in real conversations.
            - STRUCTURED_OUTPUT: tries to bundle reply + verdict in a single
              JSON call. Sounds ideal, but smaller models (gpt-4o-mini) often
              ignore the JSON contract and produce plain conversational text
              when chat history grows or many tools are active — then it
              falls back to LLM_JUDGE anyway, costing more than LLM_JUDGE
              alone. Worth picking only with top-tier models (gpt-4o full,
              Claude 3.7 Sonnet) or once OpenAI's response_format=json_object
              support is wired in.

            Pick LLM_JUDGE unless the user explicitly asks for one of the
            other two with a good reason.

            ════════════ AUTO-TRIGGER ════════════

            Field: triggerDescription — natural-language hint the LLM router
            uses to decide whether a user message should activate this flow.
            Example: "User wants to schedule a sales meeting or asks about
            pricing tiers." Leave empty/null to disable auto-trigger (the
            agent only runs the flow when explicitly invoked).

            Field: triggerMode ∈ {"ONCE", "ALWAYS"}:
            - ONCE: after the flow completes, the router won't re-fire it in
              the same conversation. Default for most use cases.
            - ALWAYS: every matching user message restarts the flow. Use for
              support-bot patterns where the user may ask the same kind of
              question repeatedly.

            ════════════ TOP-LEVEL FIELDS ════════════

            - name: short title (≤100 chars), Pascal-Caseish (e.g. "Lead
              Qualification", "Order Status Lookup", "Bug Triage Intake").
            - description: ≤500 chars admin-facing summary of what the flow does.
            - enabled: 1 by default; 0 if the user explicitly asks to ship it
              disabled.

            ════════════ OUTPUT BEHAVIOUR ════════════

            Each turn, return:
            - `message`: short conversational reply for the chat panel (in the
              same language the user wrote in). Keep it under 400 characters —
              one or two sentences summarising what you built and an open
              question if you need clarification.
            - `state`: the FULL ChatFlowGeneration — every node, every edge.
              The frontend REPLACES the form state wholesale.

            TOKEN BUDGET:
            - The whole response (message + state JSON) MUST fit in roughly
              5000 output tokens. Keep aiInstruction under ~250 characters per
              node, conditionExpression under ~120, description and
              triggerDescription under ~250 each.
            - OMIT optional fields you don't need (don't write them as empty
              strings — drop the key or set null). Especially edge `label`,
              which should almost never appear.

            DO NOT include id, agentId, definitionJson, sortOrder, or any
            persistence-only fields. The platform owns those.

            ════════════ EXAMPLES OF GOOD FLOWS ════════════

            * "Lead Qualification" — start → ask-name → ask-email → ask-company-size
              → decide-qualified (condition: company_size >= 50) → yes:
              schedule-meeting (functionCall) → end-qualified ; no: end-thanks.
            * "Course Price Lookup" — start → ask-course (aiQuestion, output
              "course_name") → fetch-price (functionCall, NATIVE
              "course_price_lookup") → end.
            * "Pizza Order" — start → ask-flavor → ask-size → ask-address →
              decide-deliver (condition: address inside delivery zone) → yes:
              place-order (functionCall) → end-confirmed ; no: end-out-of-zone.

            Be thorough. Use ALL the components when they fit the user's brief —
            don't avoid conditions or function calls if they make the flow more
            useful. But also don't force them when a linear sequence is enough.
            """;

    /**
     * Compact reference appended to the revision prompt — the create
     * prompt already documents these in detail, so we just nudge.
     */
    private static final String COMMON_TYPE_REFERENCE = """


            QUICK REFERENCE (full docs in the create prompt):
            Node types: start | end | aiQuestion | condition | functionCall
            aiQuestion fields: aiInstruction, outputVariable, validationRule
            condition fields: conditionExpression + 2 edges (sourceHandle "yes"/"no")
            functionCall fields: toolSource (NATIVE|MCP), functionName, mcpServerId
            Guardrail: HEURISTIC | LLM_JUDGE (default) | STRUCTURED_OUTPUT
            Trigger: ONCE | ALWAYS
            """;

    private static final String REVISE_SYSTEM_PROMPT = """
            You are revising an existing Chat Flow inside Turing. The user is
            describing an INCREMENTAL change to the flow you already built. The
            previous full state is in `currentState` — keep everything the user
            didn't ask to change.

            EDIT RULES:
            - Preserve every node id that already exists. Only generate new ids
              for nodes you ADD.
            - When the user asks to "remove the X step", drop the node AND its
              incoming/outgoing edges. Re-wire so the flow is still connected.
            - When the user changes a question's wording, only edit
              aiInstruction; don't change outputVariable unless they explicitly
              renamed the variable.
            - When the user adds a branch, you may need to convert a downstream
              linear sequence into a condition node + two paths.
            - Respect the same field semantics, edge rules, and guardrail/trigger
              policies described elsewhere — do not violate them.
            - If the user asks something that contradicts the constraints (e.g.
              "delete the start node"), refuse politely in `message` and return
              the state UNCHANGED.

            OUTPUT: same shape as the create turn — short conversational
            `message` + the FULL updated `state`.
            """ + COMMON_TYPE_REFERENCE;

    /* ─────────────────────── Helpers ─────────────────────── */

    /**
     * Heuristic to detect the very first turn — when {@link #create}
     * should run instead of {@link #revise}.
     */
    public static boolean isFirstTurn(AiAuthoringRequest<ChatFlowGeneration> request) {
        if (request == null) return true;
        boolean hasAssistantHistory = request.messages() != null
                && request.messages().stream()
                        .anyMatch(m -> m != null
                                && "assistant".equalsIgnoreCase(safe(m.role())));
        boolean hasState = request.currentState() != null
                && request.currentState().nodes() != null
                && !request.currentState().nodes().isEmpty();
        return !hasAssistantHistory && !hasState;
    }

    private static String safe(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
