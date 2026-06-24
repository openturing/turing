export interface TurMcpServer {
  id: string;
  title: string;
  description: string;
  /** Guidance injected into the agent system prompt when this enabled server is attached. */
  llmInstructions?: string | null;
  /** Companion brief for the llmInstructions "Help me write" affordance (UI-only). */
  llmInstructionsMetaPrompt?: string | null;
  url?: string;
  command?: string;
  args?: string;
  type: "ASYNC" | "SYNC";
  connectionType: "HTTP" | "COMMAND";
  /**
   * T294 — wire transport for HTTP servers. `SSE` is the legacy two-endpoint
   * HTTP+SSE protocol; `STREAMABLE_HTTP` is the single-endpoint Streamable
   * HTTP protocol. Null/absent means SSE (legacy default). Ignored for
   * COMMAND servers.
   */
  transportType?: "SSE" | "STREAMABLE_HTTP" | null;
  enabled: number;
  icon?: string | null;
  /** T372 — null for the shared GLOBAL BYO-infra pool (read-only when tenancy is on). */
  tenantId?: string | null;
}
