/**
 * T1032 — the sentinel that removes a stored MCP credential. A blank field
 * cannot mean "clear": that is what the form shows for a token it may not read.
 */
export const MCP_AUTH_TOKEN_CLEAR = "__clear__";

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
  /**
   * T1032 — write-only bearer token for an authenticated HTTP MCP server. Sent
   * on save and never returned; leave it out (or blank) to keep the stored one.
   * Submit {@link MCP_AUTH_TOKEN_CLEAR} to remove it.
   */
  authToken?: string | null;
  /** T1032 — read-only: whether a credential is stored. The token itself never leaves the server. */
  authTokenSet?: boolean;
  enabled: number;
  icon?: string | null;
  /** T372 — null for the shared GLOBAL BYO-infra pool (read-only when tenancy is on). */
  tenantId?: string | null;
}
