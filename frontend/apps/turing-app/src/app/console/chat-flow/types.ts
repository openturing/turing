/**
 * Types for the Chat Flow builder. The shape mirrors what the backend Spring AI Advisor will
 * consume: a list of typed nodes (with AI instructions / routing metadata) and a list of edges.
 *
 * @since 2026.2.4
 */

export type FlowNodeType =
  | "start"
  | "end"
  | "aiQuestion"
  | "formCapture"
  | "condition"
  | "functionCall"
  | "scheduleAgent"
  | "subFlow"
  | "subFlowSwitch"
  | "persona"
  | "switch"
  | "slot"
  | "writeSlot"
  | "webhook"
  | "suspend"
  | "humanApproval"
  | "planningStep"
  | "iteratePlan";

/**
 * Notification channel a {@code humanApproval} node (T119) uses to alert an
 * operator: e-mail (target = address), Slack (target = incoming-webhook URL),
 * or an admin-declared webhook (target = webhook name).
 * @since 2026.3.1
 */
export type FlowApprovalChannel = "email" | "slack" | "webhook";

/**
 * What a {@code humanApproval} node does when its timeout elapses without an
 * operator decision: auto-reject (default) or auto-approve. The matching
 * decision string is written into the approval slot before the flow resumes.
 * @since 2026.3.1
 */
export type FlowApprovalTimeoutBehavior = "auto_reject" | "auto_approve";

/**
 * Completion policy for an {@code iteratePlan} node (T108-2): what happens to
 * the in-flight plan item once its body sub-flow finishes.
 * - {@code mark_done} (default): flip the item's status to "done" — the plan
 *   is preserved as an auditable record of what ran.
 * - {@code remove}: drop the item from the plan entirely (queue-drain).
 * Both modes guarantee the iteration terminates.
 * @since 2026.3.1
 */
export type FlowCompletionMode = "mark_done" | "remove";

export type FlowToolSource = "NATIVE" | "MCP";

/**
 * Operations supported by the {@code slot} node. {@code SET} writes a literal
 * value to {@link FlowNodeData.slotName} in the flow's variable map (honoring
 * {@link FlowNodeData.overrideExistingValue} when the slot already has a
 * value); {@code DELETE} removes the slot entirely.
 *
 * @since 2026.2.7
 */
export type FlowSlotOperation = "SET" | "DELETE";

/**
 * Soft-fail policy applied by the {@code LLM_JUDGE} guardrail when the
 * judge rejects the user's reply on a slot-collecting aiQuestion node.
 *
 * - {@code advance_with_literal}: after retry, force-capture the user's
 *   message into the slot and advance regardless of the judge's redirect.
 *   Recommended for free-form prose slots (cargo, objetivo, tema livre)
 *   where the judge occasionally rejects valid compound answers and
 *   would leave the flow stuck with a null slot.
 * - {@code reprompt}: default. Trust the judge's redirect when it carries
 *   a substantive message; force-capture only when both retries returned
 *   blank redirects (legacy heuristic).
 * - {@code block}: never force-capture. Always trust the judge's
 *   rejection. Use on critical-validation slots (CPF, email, payment)
 *   where capturing a wrong value is worse than reprompting.
 *
 * @since 2026.2.31
 */
export type FlowOnJudgeReject = "advance_with_literal" | "reprompt" | "block";

/**
 * A single branch on a Switch node. The `id` is used as the `sourceHandle` of any outgoing edge,
 * so it must remain stable across renames (the label can change, but renaming an id would orphan
 * the wired edges).
 *
 * @since 2026.2.7
 */
export interface FlowSwitchOption {
  id: string;
  label: string;
  /**
   * Only used by {@code subFlowSwitch} nodes (T47): when this option wins the
   * classification cascade, the engine descends into the named sub-flow
   * instead of routing through an outgoing edge. Ignored on plain
   * {@code switch} nodes (which route via {@code id} → {@code sourceHandle}).
   * @since 2026.3.1
   */
  subFlowId?: string;
  /** Cached display name of the linked sub-flow — same UX pattern as {@link FlowNodeData.subFlowName}. */
  subFlowName?: string;
}

/**
 * A single arm of a per-node A/B experiment (T72). When assigned to a
 * conversation, the variant's non-blank {@link aiInstruction} replaces the
 * node's base instruction at prompt-build time; a blank override marks the
 * "control" arm (keep the authored instruction).
 *
 * @since 2026.3.1
 */
export interface FlowNodeVariant {
  /** Human-readable arm name — surfaces in the `[A/B Node Trace]` log. */
  label?: string;
  /** Relative traffic weight. null/0 excludes the arm when others have weight; all-zero = uniform split. */
  weight?: number;
  /** Swapped instruction text. Blank/omitted = reuse the node's base instruction (control arm). */
  aiInstruction?: string;
}

export interface FlowNodeData extends Record<string, unknown> {
  label: string;
  type: FlowNodeType;
  /** Prompt sent to the LLM when the node is an AI question or condition-on-AI step. */
  aiInstruction?: string;
  /** Variable name where the collected value is stored in the flow context (e.g. "email"). */
  outputVariable?: string;
  /**
   * When true on an aiQuestion node, the engine always asks the question and overwrites any
   * value already collected into {@link outputVariable}. When false/omitted and the slot
   * already has a non-blank value, the engine skips the node (transparent transition).
   * @since 2026.2.7
   */
  overrideExistingValue?: boolean;
  /** Validation rule for AI question outputs. */
  validationRule?: string;
  /**
   * Suggested chip labels surfaced beside an AI Question — picking one sends the label as the
   * user message (no branching). Unlike {@link switchOptions}, these are pure UX hints and do
   * not affect routing. @since 2026.2.7
   */
  inlineOptions?: string[];
  /** Target function name for Function Call nodes — Spring AI @Tool method when toolSource=NATIVE. */
  functionName?: string;
  /** Condition expression for Condition nodes. */
  conditionExpression?: string;
  /** Origin of the tool callable for Function Call nodes: Turing native @Tool or an MCP server. */
  toolSource?: FlowToolSource;
  /** MCP server id to expose as the tool source when toolSource=MCP. */
  mcpServerId?: string;
  /** Id of another chat flow within the same AI agent to chain into when this Sub Flow node runs. */
  subFlowId?: string;
  /** Cached display name of the linked sub-flow — kept on the node so the canvas reads well even before the flow list loads. */
  subFlowName?: string;
  /** Persona id this node should switch the conversation's active voice to. Only used by `persona` nodes. @since 2026.2.7 */
  personaId?: string;
  /** Cached display name of the linked persona — same pattern as {@link subFlowName}, keeps the canvas readable before the persona catalog finishes loading. @since 2026.2.7 */
  personaName?: string;
  /** Branches exposed by a Switch node — each option becomes a labeled source handle. @since 2026.2.7 */
  switchOptions?: FlowSwitchOption[];
  /** Optional flow-context variable name where the matched Switch option label is stored. @since 2026.2.7 */
  switchVariable?: string;
  /**
   * Slot the {@code slot} node operates on — must be one of the agent's
   * declared slots so the value lands in a stable, typed schema.
   * @since 2026.2.7
   */
  slotName?: string;
  /** {@code SET} writes {@link slotValue}; {@code DELETE} removes the slot. @since 2026.2.7 */
  slotOperation?: FlowSlotOperation;
  /**
   * Literal value written to {@link slotName} by a {@code SET} operation.
   * Ignored when {@link slotOperation} is {@code DELETE}.
   * @since 2026.2.7
   */
  slotValue?: string;
  /**
   * Soft-fail policy on this aiQuestion node when the LLM_JUDGE guardrail
   * rejects the user's reply. See {@link FlowOnJudgeReject} for the mode
   * semantics. Omitted/null defaults to {@code reprompt}, preserving the
   * pre-2026.2.31 behavior on existing flows.
   * @since 2026.2.31
   */
  onJudgeReject?: FlowOnJudgeReject;
  /**
   * Declarative tool-availability switch for an aiQuestion node
   * (§I.5 step 3 of the harness inversion).
   * - `true` (or `undefined`/`null`) — tools are available to the LLM.
   *   Default — preserves pre-2026.2.7 behavior.
   * - `false` — tools are STRIPPED. The backend builds the ChatOptions
   *   with an empty toolCallbacks list and internalToolExecutionEnabled(false).
   *   Replaces the legacy "NÃO CHAME NENHUMA TOOL" sentinel in
   *   {@link aiInstruction}.
   *
   * Used by flows that need to gate the LLM from preemptively calling
   * tools (canonical case: a coupon-capture node where the proposal tool
   * MUST wait until the slot is populated).
   * @since 2026.2.7
   */
  toolsEnabled?: boolean;
  /**
   * Tools the LLM must have available on this node regardless of the
   * §IV.2 / T29 BM25 pre-filter. When the backend's tool-prefilter is
   * active (catalog grows past `turing.genai.tool-prefilter.min-tools-threshold`),
   * it ranks tools by similarity to the user message and keeps only the
   * top-K. Tools listed here always survive, even if the user's wording
   * doesn't lexically match the tool's description — use this on nodes
   * that depend on a specific tool firing (e.g. a "compose proposal"
   * node where the proposal-composer tool must remain callable after a
   * slot is captured).
   *
   * Names must match the `@Tool(name)` value (native), the MCP tool
   * name, or the custom-tool registered name. Empty/undefined means
   * "no protected tools on this node" — the BM25 filter is free to drop
   * anything below the cutoff.
   * @since 2026.3.1
   */
  requiredTools?: string[];
  /**
   * Id of the {@code TurRoutine} a {@code scheduleAgent} node fires when
   * the engine walks across it. The routine runs asynchronously through
   * the JMS queue; its result lands in {@link outputVariable} via the
   * slot bus, at which point the auto-resume listener advances the flow.
   * @since 2026.3.1
   */
  routineId?: string;
  /** Cached display name of the linked routine — same UX pattern as {@link subFlowName}. */
  routineName?: string;
  /**
   * Per-node timeout override (ms) for a {@code scheduleAgent} node. Blank
   * falls back to the routine's own {@code defaultTimeoutMs}. On expiry
   * the engine takes the {@code timeout} outgoing edge (or the first edge
   * when none is wired).
   * @since 2026.3.1
   */
  routineTimeoutMs?: number;
  /**
   * When `true` on a `functionCall` or `scheduleAgent` node (T49), a
   * tool/routine failure routes the flow along the outgoing edge whose
   * `sourceHandle` is `"failure"` — authors wire an alternative path for
   * graceful recovery (apologize-then-retry, ask user to re-input,
   * escalate to handoff). When `false`/`undefined`, the engine logs the
   * failure and advances to the first outgoing edge (legacy behavior).
   *
   * Without a wired `failure` edge, the engine falls back to the first
   * outgoing edge — same lenient default the rest of the flow ops use
   * (subFlowSwitch wildcard, scheduleAgent timeout fallback). The editor
   * surfaces this branch as a red try/catch edge in T50.
   * @since 2026.3.1
   */
  continueOnFailure?: boolean;
  /**
   * Per-node A/B experiment key (T72). When non-blank and at least one
   * {@link nodeVariants} arm is declared, the engine swaps this single node's
   * `aiInstruction` for the variant deterministically (and stickily) assigned
   * to the conversation — granular optimization without duplicating the flow.
   * Independent namespace from the flow-level `experimentKey` (T67). Blank/
   * omitted (default) means the node behaves exactly as authored.
   * @since 2026.3.1
   */
  nodeExperimentKey?: string;
  /**
   * Variant arms of this node's per-node A/B experiment (T72). Each arm may
   * override {@link aiInstruction}; a blank override is the control arm.
   * Empty/omitted means no per-node experiment.
   * @since 2026.3.1
   */
  nodeVariants?: FlowNodeVariant[];
  /**
   * T107 — declared fields of a `formCapture` node rendered as a NATIVE
   * multi-field form by the SDK. When present, the engine emits a structured
   * `"form"` SSE event with these fields and the node is satisfied once every
   * required field's slot is filled (instead of a single-turn capture into
   * {@link outputVariable}). Omit for the legacy single-field behavior.
   * @since 2026.3.1
   */
  formFields?: FlowFormField[];
  /**
   * T108-1 — schema hint for a `planningStep` node: a free-form description of
   * the JSON item shape the LLM should emit into the plan slot (reuses
   * {@link outputVariable}, default `__plan`). Passed verbatim into the
   * planning prompt; the backend normalizes whatever the model returns to the
   * canonical `{id, title, status}` array regardless. Optional.
   * @since 2026.3.1
   */
  planSchema?: string;
  /**
   * T108-2 — completion policy for an `iteratePlan` node. See
   * {@link FlowCompletionMode}. Omitted/`mark_done` is the default and stays
   * implicit in the export JSON.
   * @since 2026.3.1
   */
  completionMode?: FlowCompletionMode;
  /**
   * T119 — configuration for a `humanApproval` node. Mirrors the backend
   * `ChatFlowNode.HumanApprovalConfig` record: the engine fires a notification
   * on {@link FlowApprovalChannel} to a target, parks the conversation, and
   * advances once the operator's decision lands in the approval slot. Present
   * only on `humanApproval` nodes.
   * @since 2026.3.1
   */
  humanApproval?: FlowHumanApprovalConfig;
}

/**
 * Configuration of a `humanApproval` node (T119). Stored nested under
 * `node.data.humanApproval` to match the backend record shape.
 * @since 2026.3.1
 */
export interface FlowHumanApprovalConfig {
  /** Notification channel used to alert the operator. */
  channel?: FlowApprovalChannel;
  /** Channel-specific destination: e-mail address, Slack URL, or webhook name. */
  target?: string;
  /** Message body; `{{slot}}` placeholders are substituted with slot values. */
  template?: string;
  /** Slot the decision lands in (approve | reject | edit:<text>). */
  approvalSlot?: string;
  /** Seconds to wait before auto-resolving; 0/omitted = wait indefinitely. */
  timeoutSeconds?: number;
  /** Decision applied on timeout. Defaults to `auto_reject`. */
  timeoutBehavior?: FlowApprovalTimeoutBehavior;
}

/**
 * One field of a `formCapture` node's native multi-field form (T107). Mirrors
 * the backend `ChatFlowNode.FormField` record; each field maps to one slot.
 * @since 2026.3.1
 */
export interface FlowFormField {
  /** Slot the captured value is written to. */
  name: string;
  /** Human-readable label rendered above the input. */
  label?: string;
  /** Widget hint: text | email | tel | number | date | textarea | select. */
  type?: string;
  /** When false, the field is optional and does not block submission. */
  required?: boolean;
  /** Optional input placeholder text. */
  placeholder?: string;
  /** Validation-rule hint (email, phone, cpf, …) for client-side checks. */
  validationRule?: string;
  /** Choice labels for a `select` field; ignored for other types. */
  options?: string[];
}

export const NODE_COLORS: Record<FlowNodeType, { bg: string; border: string; text: string }> = {
  start: {
    bg: "bg-emerald-500",
    border: "border-emerald-600",
    text: "text-white",
  },
  end: {
    bg: "bg-rose-500",
    border: "border-rose-600",
    text: "text-white",
  },
  aiQuestion: {
    bg: "bg-blue-50 dark:bg-blue-950/40",
    border: "border-blue-500",
    text: "text-blue-900 dark:text-blue-100",
  },
  condition: {
    bg: "bg-amber-50 dark:bg-amber-950/40",
    border: "border-amber-500",
    text: "text-amber-900 dark:text-amber-100",
  },
  functionCall: {
    bg: "bg-orange-50 dark:bg-orange-950/40",
    border: "border-orange-500",
    text: "text-orange-900 dark:text-orange-100",
  },
  subFlow: {
    bg: "bg-violet-50 dark:bg-violet-950/40",
    border: "border-violet-500",
    text: "text-violet-900 dark:text-violet-100",
  },
  persona: {
    bg: "bg-fuchsia-50 dark:bg-fuchsia-950/40",
    border: "border-fuchsia-500",
    text: "text-fuchsia-900 dark:text-fuchsia-100",
  },
  switch: {
    bg: "bg-cyan-50 dark:bg-cyan-950/40",
    border: "border-cyan-500",
    text: "text-cyan-900 dark:text-cyan-100",
  },
  subFlowSwitch: {
    // Sits between `switch` (cyan — multi-way edge router) and `subFlow`
    // (violet — single descent). Purple-cyan blend (sky) signals "switch
    // semantics, sub-flow consequence" so authors spot the family at a glance.
    bg: "bg-purple-50 dark:bg-purple-950/40",
    border: "border-purple-500",
    text: "text-purple-900 dark:text-purple-100",
  },
  slot: {
    bg: "bg-sky-50 dark:bg-sky-950/40",
    border: "border-sky-500",
    text: "text-sky-900 dark:text-sky-100",
  },
  writeSlot: {
    // Distinct from `slot` so authors can spot at-a-glance which slot
    // nodes interpolate (writeSlot, teal) vs. write literal (slot, sky).
    bg: "bg-teal-50 dark:bg-teal-950/40",
    border: "border-teal-500",
    text: "text-teal-900 dark:text-teal-100",
  },
  webhook: {
    // Rose-pink — an outbound side-effect node (CRM push) like functionCall
    // (orange) but reaching an external HTTP endpoint, so a warmer/distinct
    // hue signals "leaves the system".
    bg: "bg-pink-50 dark:bg-pink-950/40",
    border: "border-pink-500",
    text: "text-pink-900 dark:text-pink-100",
  },
  formCapture: {
    // Indigo: visually adjacent to aiQuestion's blue (same family of
    // "capture user input" nodes) but distinct enough that authors can
    // tell which one is LLM-driven vs. regex-validated at a glance.
    bg: "bg-indigo-50 dark:bg-indigo-950/40",
    border: "border-indigo-500",
    text: "text-indigo-900 dark:text-indigo-100",
  },
  scheduleAgent: {
    // Lime signals "scheduled / time-driven" — visually adjacent to
    // functionCall's orange (both invoke a tool) but cooler so authors
    // can tell at a glance which call is async vs sync.
    bg: "bg-lime-50 dark:bg-lime-950/40",
    border: "border-lime-500",
    text: "text-lime-900 dark:text-lime-100",
  },
  suspend: {
    // T121 — slate signals "paused, awaiting external trigger". Distinct
    // from scheduleAgent's lime (active waiting on a known routine) — a
    // suspend node parks the conversation indefinitely until an external
    // POST /chat/resume call moves it forward.
    bg: "bg-slate-50 dark:bg-slate-900/40",
    border: "border-slate-500",
    text: "text-slate-900 dark:text-slate-100",
  },
  humanApproval: {
    // T119 — rose: a human-gated checkpoint. Warmer than suspend's neutral
    // slate (both park the conversation) so authors spot at a glance that this
    // pause is waiting on a *person's* decision, not an external system.
    bg: "bg-rose-50 dark:bg-rose-950/40",
    border: "border-rose-500",
    text: "text-rose-900 dark:text-rose-100",
  },
  planningStep: {
    // T108 — amber/yellow signals "think before acting". Adjacent to
    // condition's amber (both are reasoning steps) but warmer (yellow) so the
    // planning family reads distinct on the canvas.
    bg: "bg-yellow-50 dark:bg-yellow-950/40",
    border: "border-yellow-500",
    text: "text-yellow-900 dark:text-yellow-100",
  },
  iteratePlan: {
    // T108 — emerald: a loop/iteration construct. Paired visually with
    // planningStep (yellow) — plan then execute — while standing apart from the
    // single-descent subFlow (violet).
    bg: "bg-emerald-50 dark:bg-emerald-950/40",
    border: "border-emerald-500",
    text: "text-emerald-900 dark:text-emerald-100",
  },
};
