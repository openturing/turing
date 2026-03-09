import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts";
import axios from "axios";

export interface ChatMessageItem {
  role: "user" | "assistant";
  content: string;
}

export interface ChatResponse {
  role: string;
  content: string;
}

export interface ContextInfoResponse {
  contextWindow: number;
  source: string;
}

function readCsrfTokenFromCookie(): string | null {
  const match = /(?:^|;\s*)XSRF-TOKEN=([^;]+)/.exec(document.cookie);
  return match ? decodeURIComponent(match[1]) : null;
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
            const jsonStr = line.slice(5).trim();
            if (!jsonStr) continue;
            try {
              const parsed = JSON.parse(jsonStr) as ChatResponse;
              if (parsed.content) {
                onToken(parsed.content);
              }
            } catch {
              // skip malformed JSON chunks
            }
          }
        }
      }

      if (buffer.startsWith("data:")) {
        const jsonStr = buffer.slice(5).trim();
        if (jsonStr) {
          try {
            const parsed = JSON.parse(jsonStr) as ChatResponse;
            if (parsed.content) {
              onToken(parsed.content);
            }
          } catch {
            // skip
          }
        }
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
            const jsonStr = line.slice(5).trim();
            if (!jsonStr) continue;
            try {
              const parsed = JSON.parse(jsonStr) as ChatResponse;
              if (parsed.content) {
                onToken(parsed.content);
              }
            } catch {
              // skip malformed JSON chunks
            }
          }
        }
      }

      if (buffer.startsWith("data:")) {
        const jsonStr = buffer.slice(5).trim();
        if (jsonStr) {
          try {
            const parsed = JSON.parse(jsonStr) as ChatResponse;
            if (parsed.content) {
              onToken(parsed.content);
            }
          } catch {
            // skip
          }
        }
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
}
