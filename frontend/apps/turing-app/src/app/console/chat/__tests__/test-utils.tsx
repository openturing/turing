import type { ReactElement } from "react";
import { render, type RenderResult } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { vi } from "vitest";
import type { TurAIAgent } from "@/models/agent/ai-agent.model";

/** One SSE `data:` event as emitted by the Turing chat backend. */
export interface SseEvent {
  type?: "token" | "options" | "form" | "sources";
  content: string;
}

/**
 * Builds a ReadableStream that emits the given chat SSE events as
 * `data: {json}\n` lines and closes — the exact shape
 * {@code consumeAssistantStream} parses in the SDK.
 */
export function sseStream(events: SseEvent[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  const payload = events.map((e) => `data: ${JSON.stringify(e)}\n`).join("");
  return new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(encoder.encode(payload));
      controller.close();
    },
  });
}

/**
 * A `fetch` stub that resolves to a streaming SSE response carrying
 * {@code events}. `postAgentChat` / `postLlmChat` only read `ok` + `body`,
 * so a minimal Response-like object is enough.
 */
export function makeSseFetch(events: SseEvent[]) {
  return vi.fn(
    async (_input?: unknown, _init?: RequestInit) =>
      ({
        ok: true,
        status: 200,
        statusText: "OK",
        body: sseStream(events),
      }) as unknown as Response,
  );
}

/** Minimal AI agent fixture — only the fields the chat UI reads are set. */
export function makeAgent(overrides: Partial<TurAIAgent> = {}): TurAIAgent {
  return {
    id: "agent-1",
    enabled: 1,
    title: "Support Agent",
    icon: null,
    llmInstances: [{ id: "llm-1" }],
    ...overrides,
  } as unknown as TurAIAgent;
}

/**
 * Renders inside the providers the full chat page expects: a fresh React
 * Query client (the header's session-info sheet calls {@code useQuery}) and a
 * MemoryRouter. A new client per call keeps query caches from leaking between
 * tests.
 */
export function renderWithRouter(ui: ReactElement): RenderResult {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  );
}
