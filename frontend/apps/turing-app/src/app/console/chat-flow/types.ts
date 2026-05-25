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
  | "subFlow"
  | "persona"
  | "switch"
  | "slot"
  | "writeSlot";

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
  formCapture: {
    // Indigo: visually adjacent to aiQuestion's blue (same family of
    // "capture user input" nodes) but distinct enough that authors can
    // tell which one is LLM-driven vs. regex-validated at a glance.
    bg: "bg-indigo-50 dark:bg-indigo-950/40",
    border: "border-indigo-500",
    text: "text-indigo-900 dark:text-indigo-100",
  },
};
