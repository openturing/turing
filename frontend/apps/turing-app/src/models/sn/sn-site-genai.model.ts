import type { TurAIAgent } from "../agent/ai-agent.model.ts";

export interface TurSNSiteGenAi {
  id: string;
  /**
   * AI Agent that powers this site's GenAI features. The agent owns the
   * RAG enabled flag, LLM list, embedding model, vector store AND the
   * system prompt used by the chat. When null, GenAI is disabled for the
   * site.
   */
  turAIAgent?: TurAIAgent | null;
  /**
   * Site description prompt used by the summary/insights LLM call. This is
   * NOT the chat system prompt (that one lives on the agent).
   */
  sitePrompt?: string;
}
