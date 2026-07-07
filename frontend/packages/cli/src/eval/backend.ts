/**
 * {@link EvalBackend} implemented over {@link TuringClient}. Chat turns hit the
 * agent-scoped streaming endpoint; slot / audit / node reads use the admin
 * session-inspection endpoints (keyed on conversation id only), so the runner
 * interface stays agent-agnostic for those.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

import type { TuringClient } from "../client.js";
import type { AuditEntry, EvalBackend, TurnInput } from "./runner.js";

interface ChatResponse {
  role: string;
  content: string;
  type?: string;
}

export class ClientEvalBackend implements EvalBackend {
  constructor(private readonly client: TuringClient) {}

  async runTurn(input: TurnInput): Promise<string> {
    let text = "";
    await this.client.streamChat(
      `/api/v2/ai-agent/${encodeURIComponent(input.agentId)}/chat`,
      {
        llmInstanceId: input.llm ?? null,
        messages: input.messages,
        conversationId: input.conversationId,
        flowId: input.flow ?? null,
      },
      (data) => {
        try {
          const r = JSON.parse(data) as ChatResponse;
          // Only the default "token" frames carry assistant prose; options /
          // sources / form frames are metadata and don't belong in the text.
          if ((r.type ?? "token") === "token" && r.content) text += r.content;
        } catch {
          // Non-JSON keep-alive — ignore.
        }
      },
    );
    return text;
  }

  async getSlots(conversationId: string): Promise<Record<string, string>> {
    const dto = await this.client.get<{ slots?: Record<string, string> }>(
      `/api/chat/sessions/${encodeURIComponent(conversationId)}/slots`,
    );
    return dto.slots ?? {};
  }

  async getAudit(conversationId: string): Promise<AuditEntry[]> {
    const dto = await this.client.get<{ entries?: AuditEntry[] }>(
      `/api/chat/sessions/${encodeURIComponent(conversationId)}/slot-audit`,
    );
    return dto.entries ?? [];
  }

  async getNode(conversationId: string): Promise<string | null> {
    const dto = await this.client.get<{ currentNodeId?: string | null }>(
      `/api/chat/sessions/${encodeURIComponent(conversationId)}/state`,
    );
    return dto.currentNodeId ?? null;
  }
}
