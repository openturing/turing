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
  | "RAG"
  /** T618 — a verbatim captured past turn (the whole system message as one block). */
  | "CAPTURED";

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

/** Source bucket for a history-prefix message (T617) / captured turn (T618). */
export type TurSystemPromptMessageOrigin =
  | "MEMORY_SUMMARY"
  | "MEMORY_RELEVANCE"
  /** T618 — a message from a verbatim captured past turn. */
  | "CAPTURED";

/**
 * Block AL/T617 — one history-prefix message the runtime prepends to the client
 * history (the T115 memory summary, the T30 relevance-retrieved turns). Mirrors
 * the backend `TurSystemPromptMessageDto`.
 */
export interface TurSystemPromptMessage {
  /** `"user"` or `"assistant"` — the turn type the runtime builds. */
  role: string;
  origin: TurSystemPromptMessageOrigin;
  label: string;
  /** Literal body, or empty for an informational `runtimeOnly` placeholder. */
  content: string;
  tokens: number;
  /** Only materializes once the conversation accumulates history — shown as an example. */
  runtimeOnly: boolean;
  note: string | null;
}

/** A tool exposed to the LLM (sent as a schema channel, not in the prompt text). */
export interface TurSystemPromptTool {
  name: string;
  description: string | null;
  source: "CUSTOM" | "NATIVE" | "MCP";
}

/**
 * Block AK/T612 — one tool the model actually called during a replayed
 * conversation (from the T427 trace). Mirrors `TurSystemPromptToolCallDto`.
 */
export interface TurSystemPromptToolCall {
  name: string;
  /** Redacted/truncated argument digest — never raw secrets. */
  argsSummary: string | null;
  status: string;
  durationMs: number;
}

/**
 * Block AK/T612 — the "replayed from a real conversation" envelope. Present only
 * when the preview was rebuilt from a conversation's persisted state (the
 * ground-truth prompt for that turn) rather than synthesized. Mirrors
 * `TurSystemPromptReplayDto`.
 */
export interface TurSystemPromptReplay {
  conversationId: string;
  /** False when the id had no persisted state (preview fell back to a plain turn). */
  resolved: boolean;
  flowName: string | null;
  nodeId: string | null;
  personaName: string | null;
  /** The conversation's recorded tool calls, oldest first; empty when none traced. */
  toolCalls: TurSystemPromptToolCall[];
  /** T618 — the captured turn being shown verbatim, or null for a T612 current-state replay. */
  turnIndex: number | null;
  /** T618 — true when assembledText/messages were loaded byte-for-byte from a captured past turn. */
  verbatim: boolean;
  /** T618 — epoch millis the shown turn was captured, or 0 when not applicable/unknown. */
  capturedAt: number;
  /** T618 — captured turns available for this conversation (newest first), for the turn picker. */
  availableTurns: TurSystemPromptCapturedTurn[];
}

/**
 * T618 — one selectable entry in the Live Preview's captured-turn picker.
 * Mirrors `TurSystemPromptCapturedTurnDto`.
 */
export interface TurSystemPromptCapturedTurn {
  turnIndex: number;
  /** Epoch millis the turn was captured (0 when the backend reported no timestamp). */
  capturedAt: number;
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
  /** Block AL/T617 — the history-prefix messages prepended to the client history; empty when none. */
  messages: TurSystemPromptMessage[];
  /** Block AK/T612 — set when this preview was replayed from a real conversation; null otherwise. */
  replay: TurSystemPromptReplay | null;
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
