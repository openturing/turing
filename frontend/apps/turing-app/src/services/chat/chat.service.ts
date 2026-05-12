import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts";
import axios from "axios";

export interface ChatMessageItem {
  role: "user" | "assistant" | "system";
  content: string;
}

export interface ChatResponse {
  role: string;
  content: string;
  /**
   * Discriminates the payload:
   *   - "token" (default, also when omitted by older backends) — content is assistant text.
   *   - "options" — content is a JSON-encoded array of strings to render as clickable chips
   *     below the latest assistant message. Picking one sends that label as the next user
   *     message; free-text input stays available.
   *
   * @since 2026.2.7
   */
  type?: "token" | "options";
}

export interface ContextInfoResponse {
  contextWindow: number;
  source: string;
}

function readCsrfTokenFromCookie(): string | null {
  const match = /(?:^|;\s*)XSRF-TOKEN=([^;]+)/.exec(document.cookie);
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * Parses one SSE `data: ...` JSON line. When the payload carries `type: "options"`, the content
 * is a JSON-encoded `string[]` of chip labels and is dispatched to {@code onOptions}; otherwise
 * the content is appended to the assistant message via {@code onToken}. Malformed JSON and
 * malformed options arrays are silently ignored so a flaky chunk never breaks the stream.
 *
 * @since 2026.2.7
 */
function handleSseLine(
  jsonStr: string,
  onToken: (token: string) => void,
  onOptions?: (labels: string[]) => void,
): void {
  if (!jsonStr) return;
  let parsed: ChatResponse;
  try {
    parsed = JSON.parse(jsonStr) as ChatResponse;
  } catch {
    return;
  }
  if (parsed.type === "options") {
    if (!onOptions) return;
    try {
      const labels = JSON.parse(parsed.content);
      if (Array.isArray(labels) && labels.every((l): l is string => typeof l === "string")) {
        onOptions(labels);
      }
    } catch {
      // skip malformed options payload
    }
    return;
  }
  if (parsed.content) {
    onToken(parsed.content);
  }
}

async function ensureCsrfToken(): Promise<string | null> {
  let token = readCsrfTokenFromCookie();
  if (token) return token;

  const baseURL = axios.defaults.baseURL ?? "";
  const response = await fetch(`${baseURL}/csrf`, { credentials: "include" });
  if (response.ok) {
    token =
      response.headers.get("X-XSRF-TOKEN") ??
      readCsrfTokenFromCookie();
  }
  return token;
}

export class TurChatService {
  async sendStream(
    llmInstanceId: string,
    messages: ChatMessageItem[],
    onToken: (token: string) => void,
    onDone: () => void,
    onError: (error: Error) => void,
    files?: File[],
  ): Promise<void> {
    // `sendStream` is the vanilla per-LLM chat — no flow context, so no chip options.
    const options: { onOptions?: (labels: string[]) => void } | undefined = undefined;
    const baseURL = axios.defaults.baseURL ?? "";
    const url = `${baseURL}/v2/llm/${llmInstanceId}/chat`;
    const csrfToken = await ensureCsrfToken();

    const headers: Record<string, string> = {
      Accept: "text/event-stream",
    };
    if (csrfToken) {
      headers["X-XSRF-TOKEN"] = csrfToken;
    }

    let body: BodyInit;
    if (files && files.length > 0) {
      const formData = new FormData();
      formData.append(
        "request",
        new Blob([JSON.stringify({ messages })], { type: "application/json" }),
      );
      for (const file of files) {
        formData.append("files", file);
      }
      body = formData;
    } else {
      headers["Content-Type"] = "application/json";
      body = JSON.stringify({ messages });
    }

    try {
      const response = await fetch(url, {
        method: "POST",
        headers,
        credentials: "include",
        body,
      });

      if (!response.ok) {
        if (response.status === 401 && !globalThis.location.pathname.startsWith("/login")) {
          const returnUrl = globalThis.location.pathname + globalThis.location.search;
          globalThis.location.href = `/login?returnUrl=${encodeURIComponent(returnUrl)}`;
          return;
        }
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error("No response body");
      }

      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() ?? "";

        for (const line of lines) {
          if (line.startsWith("data:")) {
            handleSseLine(line.slice(5).trim(), onToken, options?.onOptions);
          }
        }
      }

      if (buffer.startsWith("data:")) {
        handleSseLine(buffer.slice(5).trim(), onToken, options?.onOptions);
      }

      onDone();
    } catch (err) {
      onError(err instanceof Error ? err : new Error(String(err)));
    }
  }

  async queryLLMInstances(): Promise<TurLLMInstance[]> {
    const response = await axios.get<TurLLMInstance[]>("/llm");
    return response.data;
  }

  async fetchContextInfo(llmInstanceId: string): Promise<ContextInfoResponse> {
    const response = await axios.get<ContextInfoResponse>(
      `/v2/llm/${llmInstanceId}/chat/context-info`,
    );
    return response.data;
  }

  async sendSemanticStream(
    llmInstanceId: string,
    messages: ChatMessageItem[],
    onToken: (token: string) => void,
    onDone: () => void,
    onError: (error: Error) => void,
  ): Promise<void> {
    // Semantic chat is unscoped from any flow runtime — no chip options.
    const options: { onOptions?: (labels: string[]) => void } | undefined = undefined;
    const baseURL = axios.defaults.baseURL ?? "";
    const url = `${baseURL}/v2/llm/${llmInstanceId}/semantic-chat`;
    const csrfToken = await ensureCsrfToken();

    const headers: Record<string, string> = {
      Accept: "text/event-stream",
      "Content-Type": "application/json",
    };
    if (csrfToken) {
      headers["X-XSRF-TOKEN"] = csrfToken;
    }

    try {
      const response = await fetch(url, {
        method: "POST",
        headers,
        credentials: "include",
        body: JSON.stringify({ messages }),
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error("No response body");
      }

      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() ?? "";

        for (const line of lines) {
          if (line.startsWith("data:")) {
            handleSseLine(line.slice(5).trim(), onToken, options?.onOptions);
          }
        }
      }

      if (buffer.startsWith("data:")) {
        handleSseLine(buffer.slice(5).trim(), onToken, options?.onOptions);
      }

      onDone();
    } catch (err) {
      onError(err instanceof Error ? err : new Error(String(err)));
    }
  }

  async fetchSemanticContextInfo(llmInstanceId: string): Promise<ContextInfoResponse> {
    const response = await axios.get<ContextInfoResponse>(
      `/v2/llm/${llmInstanceId}/semantic-chat/context-info`,
    );
    return response.data;
  }

  async sendAgentStream(
    agentId: string,
    llmInstanceId: string,
    messages: ChatMessageItem[],
    onToken: (token: string) => void,
    onDone: () => void,
    onError: (error: Error) => void,
    options?: {
      conversationId?: string;
      flowId?: string;
      /**
       * Invoked when the stream carries an {@code options} event — a list of suggested
       * chip labels the UI should render below the just-completed assistant message.
       * Optional: when omitted, the chip event is silently dropped (older callers keep
       * working).
       */
      onOptions?: (labels: string[]) => void;
    },
  ): Promise<void> {
    const baseURL = axios.defaults.baseURL ?? "";
    const url = `${baseURL}/v2/ai-agent/${agentId}/chat`;
    const csrfToken = await ensureCsrfToken();

    const headers: Record<string, string> = {
      Accept: "text/event-stream",
      "Content-Type": "application/json",
    };
    if (csrfToken) {
      headers["X-XSRF-TOKEN"] = csrfToken;
    }

    try {
      const response = await fetch(url, {
        method: "POST",
        headers,
        credentials: "include",
        body: JSON.stringify({
          llmInstanceId,
          messages,
          conversationId: options?.conversationId,
          flowId: options?.flowId,
        }),
      });

      if (!response.ok) {
        if (response.status === 401 && !globalThis.location.pathname.startsWith("/login")) {
          const returnUrl = globalThis.location.pathname + globalThis.location.search;
          globalThis.location.href = `/login?returnUrl=${encodeURIComponent(returnUrl)}`;
          return;
        }
        let body = "";
        try { body = await response.text(); } catch { /* ignore */ }
        throw new Error(`HTTP ${response.status}: ${response.statusText}${body ? ` — ${body}` : ""}`);
      }

      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error("No response body");
      }

      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() ?? "";

        for (const line of lines) {
          if (line.startsWith("data:")) {
            handleSseLine(line.slice(5).trim(), onToken, options?.onOptions);
          }
        }
      }

      if (buffer.startsWith("data:")) {
        handleSseLine(buffer.slice(5).trim(), onToken, options?.onOptions);
      }

      onDone();
    } catch (err) {
      onError(err instanceof Error ? err : new Error(String(err)));
    }
  }

  async fetchAgentContextInfo(agentId: string, llmInstanceId: string): Promise<ContextInfoResponse> {
    const response = await axios.get<ContextInfoResponse>(
      `/v2/ai-agent/${agentId}/chat/context-info`,
      { params: { llmInstanceId } },
    );
    return response.data;
  }

  /**
   * Drops the runtime state of a chat flow for the given conversation. The
   * next message restarts the flow at the START node.
   *
   * @returns true when a row was deleted, false when there was none.
   */
  async resetAgentFlowState(
    agentId: string,
    flowId: string,
    conversationId: string,
  ): Promise<boolean> {
    const response = await axios.delete<boolean>(`/v2/ai-agent/${agentId}/chat-flow-state`, {
      params: { flowId, conversationId },
    });
    return response.data === true;
  }
}
