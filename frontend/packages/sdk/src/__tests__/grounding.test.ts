import { describe, expect, it, vi } from "vitest";
import { createChatController } from "../controllers/chat";
import type { TuringClient } from "../client";
import type { TurChatGrounding } from "../types";

/**
 * T516 / §XXVIII.12 — the answer-grounding guardrail verdict (`grounding` SSE
 * event) flows through the vanilla chat controller onto the assistant message.
 */
describe("createChatController — answer-grounding guardrail (T516)", () => {
  /** Builds an SSE `Response` from a list of stream events. */
  function sse(events: Array<{ type?: string; content: string }>): Response {
    const body = events
      .map((e) => `data: ${JSON.stringify({ role: "assistant", ...e })}\n`)
      .join("");
    return new Response(body, {
      status: 200,
      headers: { "Content-Type": "text/event-stream" },
    });
  }

  function groundingEvent(verdict: TurChatGrounding) {
    return { type: "grounding", content: JSON.stringify(verdict) };
  }

  it("attaches a flagged guardrail verdict to the assistant bubble", async () => {
    const verdict: TurChatGrounding = {
      grounded: false,
      groundingScore: 0.18,
      relevanceScore: 0.92,
      action: "FLAG",
      categories: ["ungrounded"],
      strategy: "BEDROCK",
    };
    const fetchRaw = vi.fn().mockResolvedValueOnce(
      sse([{ content: "The capital is Atlantis." }, groundingEvent(verdict)]),
    );
    const client = { fetchRaw } as unknown as TuringClient;

    const chat = createChatController(client, {
      agent: { id: "a1", llmInstanceId: "l1" },
      conversationId: "conv1",
    });

    await chat.send("what is the capital?");

    const assistant = chat.getState().messages.find((m) => m.role === "assistant");
    expect(assistant?.content).toBe("The capital is Atlantis.");
    expect(assistant?.grounding).toEqual(verdict);
    expect(assistant?.grounding?.grounded).toBe(false);
  });

  it("leaves grounding undefined for a clean answer (no event)", async () => {
    const fetchRaw = vi
      .fn()
      .mockResolvedValueOnce(sse([{ content: "A well-grounded answer." }]));
    const client = { fetchRaw } as unknown as TuringClient;

    const chat = createChatController(client, {
      agent: { id: "a1", llmInstanceId: "l1" },
      conversationId: "conv1",
    });

    await chat.send("hello");

    const assistant = chat.getState().messages.find((m) => m.role === "assistant");
    expect(assistant?.grounding).toBeUndefined();
  });
});
