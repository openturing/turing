import { describe, expect, it, vi } from "vitest";
import { createChatController } from "../controllers/chat";
import type { TuringClient } from "../client";

/**
 * T438 / T439 — client-tool round-trip through the vanilla chat controller.
 *
 * The first turn ends with a `client_tool_call` event (the backend parked); the
 * controller runs the registered handler, POSTs the result to
 * `/v2/chat/client-tool-result`, and consumes the continuation into the same
 * assistant message.
 */
describe("createChatController — client tools (T439)", () => {
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

  function clientToolCallEvent(callId: string, name: string, args: string) {
    return { type: "client_tool_call", content: JSON.stringify({ callId, name, args }) };
  }

  it("runs the handler, posts the result, and continues the turn", async () => {
    const fetchRaw = vi
      .fn()
      .mockResolvedValueOnce(sse([clientToolCallEvent("c1", "get_location", '{"hint":"city"}')]))
      .mockResolvedValueOnce(sse([{ content: "You are in Paris." }]));
    const client = { fetchRaw } as unknown as TuringClient;

    const handler = vi.fn().mockResolvedValue({ city: "Paris" });
    const chat = createChatController(client, {
      agent: { id: "a1", llmInstanceId: "l1" },
      conversationId: "conv1",
      clientTools: { get_location: handler },
    });

    await chat.send("where am I?");

    // handler ran with the parsed args
    expect(handler).toHaveBeenCalledWith({ hint: "city" });

    // two round-trips: the chat turn + the client-tool-result resume
    expect(fetchRaw).toHaveBeenCalledTimes(2);
    const [resumeUrl, resumeInit] = fetchRaw.mock.calls[1];
    expect(resumeUrl).toBe("/v2/chat/client-tool-result");
    const body = JSON.parse((resumeInit as RequestInit).body as string);
    expect(body).toMatchObject({ conversationId: "conv1", callId: "c1", result: { city: "Paris" } });

    // the continuation text landed on the assistant bubble
    const assistant = chat.getState().messages.find((m) => m.role === "assistant");
    expect(assistant?.content).toBe("You are in Paris.");
    expect(chat.getState().status).toBe("success");
  });

  it("reports a missing handler to the agent as an error result", async () => {
    const fetchRaw = vi
      .fn()
      .mockResolvedValueOnce(sse([clientToolCallEvent("c1", "unknown_tool", "{}")]))
      .mockResolvedValueOnce(sse([{ content: "ok, recovered" }]));
    const client = { fetchRaw } as unknown as TuringClient;

    const chat = createChatController(client, {
      agent: { id: "a1", llmInstanceId: "l1" },
      conversationId: "conv1",
    });

    await chat.send("do a thing");

    const [, resumeInit] = fetchRaw.mock.calls[1];
    const body = JSON.parse((resumeInit as RequestInit).body as string);
    expect(body.error).toContain("No client tool handler registered for 'unknown_tool'");
    expect(body.result).toBeUndefined();
  });

  it("registerClientTool adds a handler after construction", async () => {
    const fetchRaw = vi
      .fn()
      .mockResolvedValueOnce(sse([clientToolCallEvent("c1", "late_tool", "{}")]))
      .mockResolvedValueOnce(sse([{ content: "done" }]));
    const client = { fetchRaw } as unknown as TuringClient;

    const chat = createChatController(client, {
      agent: { id: "a1", llmInstanceId: "l1" },
      conversationId: "conv1",
    });
    const handler = vi.fn().mockResolvedValue("ack");
    chat.registerClientTool("late_tool", handler);

    await chat.send("go");
    expect(handler).toHaveBeenCalledOnce();
    const [, resumeInit] = fetchRaw.mock.calls[1];
    expect(JSON.parse((resumeInit as RequestInit).body as string).result).toBe("ack");
  });
});
