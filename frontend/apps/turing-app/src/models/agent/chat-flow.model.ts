/**
 * Persisted chat flow. The `definitionJson` string holds the React Flow graph
 * (nodes + edges) serialized — the editor parses it back on load and saves it
 * on every change.
 *
 * @since 2026.2.5
 */
/**
 * Guard rail strategies the runtime engine can apply to a chat flow.
 *   HEURISTIC         — prompt-only contract + regex/keyword heuristics; no extra LLM calls.
 *   LLM_JUDGE         — same prompt contract plus a second LLM call that returns
 *                       a JSON verdict (on_topic / collected_value / ready_to_advance).
 *   STRUCTURED_OUTPUT — single call where the chat LLM returns a JSON object that
 *                       bundles the user reply *and* the verdict — same enforcement
 *                       as LLM_JUDGE without the extra round-trip.
 */
export type TurChatFlowGuardrailMethod = "HEURISTIC" | "LLM_JUDGE" | "STRUCTURED_OUTPUT";

/**
 * Display order for guardrail method pickers — recommended option first so
 * the default lines up with what we suggest in the UI ({@code LLM_JUDGE} is
 * the most reliable in practice; see the i18n descriptions for the trade-offs).
 */
export const CHAT_FLOW_GUARDRAIL_METHODS: TurChatFlowGuardrailMethod[] = [
  "LLM_JUDGE",
  "HEURISTIC",
  "STRUCTURED_OUTPUT",
];

/**
 * How often the LLM router may auto-trigger a flow within a conversation.
 *   ONCE   — at most once per conversation (re-trigger only after manual reset)
 *   ALWAYS — re-trigger whenever the user message matches the trigger description
 */
export type TurChatFlowTriggerMode = "ONCE" | "ALWAYS";

export const CHAT_FLOW_TRIGGER_MODES: TurChatFlowTriggerMode[] = ["ONCE", "ALWAYS"];

export interface TurChatFlow {
  /** Empty / undefined when the flow is being created — the backend assigns a UUID. */
  id?: string;
  name: string;
  description?: string | null;
  definitionJson?: string | null;
  enabled: number;
  guardrailMethod: TurChatFlowGuardrailMethod;
  /** Natural-language description used by the LLM router. Empty disables auto-trigger. */
  triggerDescription?: string | null;
  triggerMode: TurChatFlowTriggerMode;
  /**
   * Optional experiment id. Flows sharing the same {@code experimentKey}
   * are treated as A/B variants — when the router picks any of them, the
   * engine deterministically reassigns to a single variant based on
   * {@code hash(conversationId + experimentKey)} weighted by
   * {@link trafficWeight}. Sticky per conversation.
   *
   * <p>{@code null}/empty disables A/B routing for this flow.
   *
   * @since 2026.2.7
   */
  experimentKey?: string | null;
  /**
   * Human-readable variant label inside {@link experimentKey} —
   * {@code "control"}, {@code "treatment_v2"}, {@code "hero_short"}.
   * Required when {@code experimentKey} is set; surfaces in the analytics
   * dashboard as the grouping dimension.
   *
   * @since 2026.2.7
   */
  variantLabel?: string | null;
  /**
   * Relative weight (0-100) inside the experiment. Two variants at
   * 50 each = 50/50 split; 90 + 10 = 90/10. Sum across variants does
   * not have to be 100 — only ratios matter. {@code 0} pauses the arm
   * without deleting it (preserves historical assignments for analysis).
   *
   * @since 2026.2.7
   */
  trafficWeight?: number | null;
}

/* ─────────────────── AI Authoring shapes ─────────────────── */

/**
 * LLM-facing shape used by the Chat Flow AI Authoring chat. Mirrors
 * the backend {@code ChatFlowGeneration} record. Decoupled from
 * {@link TurChatFlow} so the LLM doesn't see / mutate the persistence
 * fields — the AI Chat page composes the saved {@code TurChatFlow}
 * (with {@code definitionJson} serialized from {@code nodes}+{@code edges})
 * from this snapshot before saving.
 *
 * @since 2026.2.5
 */
export interface ChatFlowGeneration {
  name: string;
  description?: string | null;
  guardrailMethod?: TurChatFlowGuardrailMethod | null;
  triggerDescription?: string | null;
  triggerMode?: TurChatFlowTriggerMode | null;
  enabled?: number | null;
  nodes: ChatFlowNodeGeneration[];
  edges: ChatFlowEdgeGeneration[];
}

/** {@code "start" | "end" | "aiQuestion" | "condition" | "functionCall"} */
export type ChatFlowNodeType =
  | "start"
  | "end"
  | "aiQuestion"
  | "condition"
  | "functionCall";

export interface ChatFlowNodeGeneration {
  /** Stable, semantic id (e.g. "ask-name"). */
  id: string;
  type: ChatFlowNodeType;
  label: string;
  aiInstruction?: string | null;
  /** aiQuestion only. */
  outputVariable?: string | null;
  /** aiQuestion only. */
  validationRule?: string | null;
  /** condition only. */
  conditionExpression?: string | null;
  /** functionCall only. */
  toolSource?: "NATIVE" | "MCP" | null;
  /** functionCall + NATIVE. */
  functionName?: string | null;
  /** functionCall + MCP. */
  mcpServerId?: string | null;
}

export interface ChatFlowEdgeGeneration {
  source: string;
  target: string;
  /** "yes" / "no" for condition branches; null otherwise. */
  sourceHandle?: string | null;
  label?: string | null;
}
