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

/** Full Live Preview payload. */
export interface TurSystemPromptPreview {
  segments: TurSystemPromptSegment[];
  tools: TurSystemPromptTool[];
  assembledText: string;
  flows: TurSystemPromptFlowOption[];
  selectedFlowId: string | null;
}

/** Sentinel flowId for "preview a turn with no chat flow governing". */
export const SYSTEM_PROMPT_FLOW_NONE = "__none__";

/** One validation finding — shares the chat-flow lint shape, plus `source`. */
export interface TurSystemPromptIssue {
  severity: "ERROR" | "WARNING" | "INFO";
  code: string;
  message: string;
  hint: string;
  source: string;
}
