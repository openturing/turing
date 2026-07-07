/**
 * Live Preview + validation models for an AI Agent's system prompt.
 * Mirrors the backend DTOs in
 * `com.viglet.turing.persistence.dto.agent` (TurSystemPromptPreviewDto,
 * TurSystemPromptSegmentDto, TurSystemPromptToolDto, TurSystemPromptIssueDto).
 *
 * @since 2026.3.1
 */

/** Source bucket the UI themes each prompt segment on. */
export type TurSystemPromptOrigin =
  | "PERSONA"
  | "AGENT"
  | "MCP"
  | "FLOW"
  | "FEW_SHOT"
  | "RAG";

/** Block AK/T609 — a span of a segment that's inert on the previewed turn. */
export interface TurSystemPromptInertRegion {
  label: string;
  /** Inclusive char offset into the segment content. */
  start: number;
  /** Exclusive char offset into the segment content. */
  end: number;
  tokens: number;
  /** Why it's inert: "heuristic" | "concierge_only" | "flow_only". */
  reason: string;
}

/** One labeled fragment of the assembled system prompt, with provenance. */
export interface TurSystemPromptSegment {
  origin: TurSystemPromptOrigin;
  /** Concrete name (persona/MCP server/flow→node), or null when generic. */
  title: string | null;
  content: string;
  /** Part of a plain turn's prompt (no flow, default persona, no RAG override). */
  included: boolean;
  /** Only materializes at runtime — shown as an example, not fixed text. */
  runtimeOnly: boolean;
  note: string | null;
  /** Block AL/T613 — best-effort `chars/4` token estimate for this segment. */
  tokens: number;
  /** Block AL/T613 — `"STABLE"` (cacheable prefix) or `"PER_TURN"`; null for info segments. */
  stability: "STABLE" | "PER_TURN" | null;
  /** Block AK/T609 — spans inert this turn (a flow neutralizes them); empty when none. */
  inertRegions: TurSystemPromptInertRegion[];
}

/** A tool exposed to the LLM (sent as a schema channel, not in the prompt text). */
export interface TurSystemPromptTool {
  name: string;
  description: string | null;
  source: "CUSTOM" | "NATIVE" | "MCP";
}

/** A chat flow the operator can pick in the preview's flow selector. */
export interface TurSystemPromptFlowOption {
  id: string;
  name: string;
  enabled: boolean;
}

/** A flow node the operator can pick in the preview's node selector (T611). */
export interface TurSystemPromptFlowNode {
  id: string;
  label: string;
  type: string;
}

/** Full Live Preview payload. */
export interface TurSystemPromptPreview {
  segments: TurSystemPromptSegment[];
  tools: TurSystemPromptTool[];
  assembledText: string;
  flows: TurSystemPromptFlowOption[];
  selectedFlowId: string | null;
  /** Block AL/T613 — grand-total token estimate across assembled segments. */
  totalTokens: number;
  /** Block AL/T614 — token estimate of the STABLE (cacheable-prefix) segments. */
  stableTokens: number;
  /** Block AL/T614 — segment index where the PER_TURN tail begins, or -1 in legacy order. */
  cacheBreakpointIndex: number;
  /** Block AK/T608 — per-turn USD cost of the assembled prompt (input only); 0 when unpriced/local. */
  estimatedCostUsd: number;
  /** Block AK/T608 — model the cost estimate is based on, or null. */
  costModelName: string | null;
  /** Block AK/T611 — the selected flow's nodes, for the node picker; empty when no flow. */
  flowNodes: TurSystemPromptFlowNode[];
  /** Block AK/T611 — the flow node this preview rendered, or null when no flow. */
  selectedNodeId: string | null;
}

/** Sentinel flowId for "preview a turn with no chat flow governing". */
export const SYSTEM_PROMPT_FLOW_NONE = "__none__";

/** One validation finding — shares the chat-flow lint shape, plus `source`. */
export interface TurSystemPromptIssue {
  severity: "ERROR" | "WARNING" | "INFO";
  code: string;
  /** English fallback; the UI prefers a `code`-keyed translation. */
  message: string;
  /** English fallback; the UI prefers a `code`-keyed translation. */
  hint: string;
  source: string;
  /** Interpolation values for the `code`-keyed i18n template (length, langs, term). */
  params?: Record<string, string>;
}
