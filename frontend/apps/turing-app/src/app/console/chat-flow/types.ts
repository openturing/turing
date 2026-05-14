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
  | "condition"
  | "functionCall"
  | "subFlow"
  | "persona"
  | "switch"
  | "slot";

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
};
