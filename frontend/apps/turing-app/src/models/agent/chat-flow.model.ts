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
 * T51 / §VII.4.e — capture-first inversion mode. Only the LLM_JUDGE guardrail
 * honours it (the other methods have no judge to invert).
 *   VALIDATE_THEN_CAPTURE — legacy default: the judge gates the capture; a
 *                           rejected reply leaves the slot null and parks the
 *                           cursor (the "flow stuck with slot=null" pattern).
 *   CAPTURE_THEN_GATE     — capture-first: the reply is written to the slot
 *                           before the judge runs (so it's never null); the
 *                           judge only grades confidence, but a failing
 *                           deterministic validationRule still re-prompts.
 *   CAPTURE_THEN_GRADE    — capture-first and always advance (unless the user
 *                           abandons); validation + judge only set the grade.
 */
export type TurChatFlowCaptureMode =
  | "VALIDATE_THEN_CAPTURE"
  | "CAPTURE_THEN_GATE"
  | "CAPTURE_THEN_GRADE";

/** Display order for the capture-mode picker — legacy/default first. */
export const CHAT_FLOW_CAPTURE_MODES: TurChatFlowCaptureMode[] = [
  "VALIDATE_THEN_CAPTURE",
  "CAPTURE_THEN_GATE",
  "CAPTURE_THEN_GRADE",
];

/**
 * How often the LLM router may auto-trigger a flow within a conversation.
 *   ONCE   — at most once per conversation (re-trigger only after manual reset)
 *   ALWAYS — re-trigger whenever the user message matches the trigger description
 */
export type TurChatFlowTriggerMode = "ONCE" | "ALWAYS";

export const CHAT_FLOW_TRIGGER_MODES: TurChatFlowTriggerMode[] = ["ONCE", "ALWAYS"];

/**
 * T25 / §II.2.1 — natural language of the flow's {@code triggerDescription}.
 * Drives the per-field analyzer selection in the procedural pre-route on the
 * backend: PT → {@code PortugueseAnalyzer}, EN → {@code EnglishAnalyzer},
 * AUTO → static stopword heuristic per route. Default AUTO so existing flows
 * keep working without admin intervention.
 *
 * @since 2026.3.1
 */
export type TurChatFlowTriggerLanguage = "AUTO" | "PT" | "EN";

export const CHAT_FLOW_TRIGGER_LANGUAGES: TurChatFlowTriggerLanguage[] = ["AUTO", "PT", "EN"];

export interface TurChatFlow {
  /** Empty / undefined when the flow is being created — the backend assigns a UUID. */
  id?: string;
  name: string;
  description?: string | null;
  definitionJson?: string | null;
  enabled: number;
  guardrailMethod: TurChatFlowGuardrailMethod;
  /**
   * T51 / §VII.4.e — capture-first inversion mode (LLM_JUDGE only). Defaults
   * to {@code "VALIDATE_THEN_CAPTURE"} server-side, preserving pre-T51
   * behaviour for existing flows.
   *
   * @since 2026.3.1
   */
  captureMode?: TurChatFlowCaptureMode;
  /**
   * T53 / §VII.4.g — opt-in abandonment auto-escalation (LLM_JUDGE only).
   * When non-blank, a judge-detected abandonment ends the flow with this
   * message (an offer to talk to a human consultant) and writes a
   * {@code handoff_offered=abandon} tracking slot, instead of just closing.
   * Null/blank keeps the legacy goodbye-and-close behaviour.
   *
   * @since 2026.3.1
   */
  abandonHandoffMessage?: string | null;
  /** Natural-language description used by the LLM router. Empty disables auto-trigger. */
  triggerDescription?: string | null;
  triggerMode: TurChatFlowTriggerMode;
  /**
   * T25 / §II.2.1 — language of {@link triggerDescription}. Picks the
   * matching Lucene analyzer in the procedural pre-route (PT light stemming
   * for {@code "PT"}, Porter for {@code "EN"}, runtime detection for
   * {@code "AUTO"}). Defaults to {@code "AUTO"} server-side.
   *
   * @since 2026.3.1
   */
  triggerLanguage?: TurChatFlowTriggerLanguage;
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
  /**
   * T70 / §VII.8.a — when true (on at least one variant under the same
   * experimentKey), the engine ignores `trafficWeight` and routes via
   * Thompson sampling on the observed conversion data. Per-flow so a
   * champion-challenger setup can pin one arm fixed-weight while the
   * other opts in.
   *
   * @since 2026.3.1
   */
  banditEnabled?: boolean | null;
  /**
   * T71 / §VII.8.b — champion-challenger auto-promotion opt-in. When true
   * on at least one variant under the same experimentKey, the daily
   * promotion job auto-promotes the statistically-significant winner
   * (trafficWeight=100) and archives the losers (trafficWeight=0 + closed
   * window). Null/false leaves the experiment under manual control — the
   * operator can still trigger promotion explicitly from the significance
   * banner.
   *
   * @since 2026.3.1
   */
  autoPromote?: boolean | null;
  /**
   * T93 / §VII.11.c — JSON mapping {@code {receivingSlot: sourceSlot}} of
   * slots this flow inherits from earlier flows on the same conversation.
   * {@code {"*":"*"}} = pass every captured slot through. Null/blank
   * disables inheritance. The editor exposes this as a "Preserve all
   * session slots" toggle (writes the wildcard) plus an advanced JSON
   * textarea for renaming / picking specific slots.
   *
   * @since 2026.3.1
   */
  slotInheritanceJson?: string | null;
}

/**
 * T85 / §VII.10.b — one row of the chat-flow funnel report. Powers the
 * sidebar visualization showing where conversations get stuck or drop
 * out in the graph.
 *
 * @since 2026.3.1
 */
export interface TurChatFlowFunnelNode {
  nodeId: string;
  label: string;
  type: string;
  cursorCount: number;
  completedCount: number;
  abandonedCount: number;
}

export interface TurChatFlowFunnelReport {
  flowId: string;
  flowName: string;
  totalStates: number;
  totalSubmissions: number;
  abandonedAtFlow: number;
  nodes: TurChatFlowFunnelNode[];
}

/**
 * T94 / §VII.11.d — one finding from the chat-flow linter, surfaced in the
 * editor's sidebar warnings panel.
 *
 * @since 2026.3.1
 */
export interface TurChatFlowLintIssue {
  nodeId: string | null;
  edgeId: string | null;
  severity: "ERROR" | "WARNING" | "INFO";
  code: string;
  message: string;
  hint: string;
}

/**
 * T91 / §VII.11.a — one pair of chat flows whose trigger descriptions overlap
 * enough that the procedural router will silently swing on small scoring deltas.
 * Surfaced by {@code GET /ai-agent/{agentId}/chat-flow/trigger-conflicts} so
 * the chat-flow editor can warn the author before the LLM-router fallback
 * masks the conflict.
 *
 * @since 2026.3.1
 */
export interface TurChatFlowTriggerConflict {
  flowAId: string;
  flowAName: string;
  flowBId: string;
  flowBName: string;
  severity: "HIGH" | "WARNING";
  similarity: number;
  intersectionSize: number;
  overlappingTokens: string[];
  suggestion: string;
}

/* ─────────────────── T97 — Variant generator ─────────────────── */

/**
 * T97 / §VII.11.g — request body for the variant-generator endpoint. The
 * author types a free-text directive (tone, length, audience) and the LLM
 * rewrites the source flow's user-facing copy without touching structure.
 *
 * @since 2026.3.1
 */
export interface TurChatFlowVariantRequest {
  instructions: string;
  /** Optional explicit name; backend falls back to "{source.name} (variant)" when blank. */
  targetName?: string | null;
}

/**
 * T97 / §VII.11.g — server response from the variant-generator endpoint.
 * The candidate is NOT persisted — the dialog shows it to the author and,
 * on accept, posts it through the standard create endpoint.
 *
 * @since 2026.3.1
 */
export interface TurChatFlowVariantResponse {
  success: boolean;
  /** Human-readable error message; populated only when {@link success} is false. */
  error?: string | null;
  /** Unpersisted candidate ({@code id == null}); populated only when {@link success} is true. */
  candidate?: TurChatFlow | null;
  /** One-line natural-language description of the rewrite the LLM applied. */
  summary?: string | null;
  /** Ids of the nodes whose copy was actually rewritten — lets the UI highlight the diff. */
  rewrittenNodes: string[];
}

/* ─────────────────── T236 — Form-label tidy pass ─────────────────── */

/**
 * T236 / §VII.13.g — one form field carried into/out of the "tidy labels"
 * pass. {@code name} (slot) and {@code type} (widget) are context only and
 * echoed back untouched; only {@code label} is rewritten.
 *
 * @since 2026.3.1
 */
export interface TurFormLabelTidyField {
  name: string;
  label: string;
  type?: string;
}

/**
 * T236 / §VII.13.g — request body for the form-label tidy endpoint.
 *
 * @since 2026.3.1
 */
export interface TurFormLabelTidyRequest {
  fields: TurFormLabelTidyField[];
}

/**
 * T236 / §VII.13.g — server response from the form-label tidy endpoint.
 * Nothing is persisted — the dialog applies the tidied labels only when the
 * author confirms the conversion. {@code fields} mirrors the request order.
 *
 * @since 2026.3.1
 */
export interface TurFormLabelTidyResponse {
  success: boolean;
  error?: string | null;
  fields?: TurFormLabelTidyField[] | null;
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
