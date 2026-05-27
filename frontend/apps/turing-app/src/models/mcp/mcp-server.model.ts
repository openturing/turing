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
  enabled: number;
  icon?: string | null;
}
