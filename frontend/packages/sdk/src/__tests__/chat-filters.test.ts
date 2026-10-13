import { describe, expect, it, vi } from "vitest";

import type { TuringClient } from "../client";
import { createChatController } from "../controllers/chat";
import { chatConversationBody, postChatConversation, type TurNLFilters } from "../index";

/**
 * T1086 — the chat conversation sends the visitor's filters, and receives the
 * ones the server read from a list-style question (T1085) as data, never as text.
 */
const SAO_PAULO: TurNLFilters = {
  available: true,
  text: "quais vagas em sao paulo",
  q: "quais vagas",
  filters: [
    {
      field: "city",
      values: ["São Paulo"],
      rangeStart: null,
      rangeEnd: null,
      sourceWords: ["sao paulo"],
      confidence: 1,
      grounded: true,
    },
  ],
  fq: ["city:São Paulo"],
  dropped: [],
};

function sse(events: Array<{ type?: string; content: string }>): Response {
  const body = events.map((e) => `data: ${JSON.stringify({ role: "assistant", ...e })}\n`).join("");
  return new Response(body, { status: 200, headers: { "Content-Type": "text/event-stream" } });
}

function sentBody(fetchRaw: ReturnType<typeof vi.fn>): Record<string, unknown> {
  const init = (fetchRaw.mock.calls[0] as unknown[])[1] as RequestInit;
  return JSON.parse(init.body as string) as Record<string, unknown>;
}

describe("chatConversationBody", () => {
  it("asks for the filters event only when there is a listener for it", () => {
    const messages = [{ role: "user" as const, content: "oi" }];

    expect(chatConversationBody(messages, "pt_BR", { onFilters: () => undefined }).includeFilters).toBe(true);
    expect(chatConversationBody(messages, "pt_BR", {}).includeFilters).toBeUndefined();
  });
});

describe("postChatConversation — T1086 filters", () => {
  it("sends the filters and hands the filters event to onFilters, not to the answer", async () => {
    const fetchRaw = vi.fn().mockResolvedValueOnce(
      sse([
        { type: "filters", content: JSON.stringify(SAO_PAULO) },
        { content: "Há 3 vagas." },
      ]),
    );
    const client = { fetchRaw } as unknown as TuringClient;
    const onFilters = vi.fn();

    const res = await postChatConversation(
      client,
      "vagas",
      [{ role: "user", content: "quais vagas em sao paulo" }],
      "pt_BR",
      { filters: { uf: ["SP"] }, onFilters },
    );

    expect(sentBody(fetchRaw)).toMatchObject({ filters: { uf: ["SP"] }, includeFilters: true });
    expect(onFilters).toHaveBeenCalledWith(SAO_PAULO);
    expect(res.content).toBe("Há 3 vagas.");
  });

  it("keeps a filters event out of the answer text even without a listener", async () => {
    const fetchRaw = vi.fn().mockResolvedValueOnce(
      sse([{ type: "filters", content: JSON.stringify(SAO_PAULO) }, { content: "ok" }]),
    );
    const client = { fetchRaw } as unknown as TuringClient;

    const res = await postChatConversation(client, "vagas", [{ role: "user", content: "x" }]);

    expect(res.content).toBe("ok");
    expect(sentBody(fetchRaw).includeFilters).toBeUndefined();
  });
});

describe("createChatController — T1086 filters", () => {
  it("sends its default filters, and a turn's own filters replace them", async () => {
    const fetchRaw = vi
      .fn()
      .mockResolvedValueOnce(sse([{ content: "a" }]))
      .mockResolvedValueOnce(sse([{ content: "b" }]));
    const get = vi.fn().mockResolvedValue({ enabled: true, reason: "NONE" });
    const client = { fetchRaw, get } as unknown as TuringClient;
    const onFilters = vi.fn();

    const chat = createChatController(client, {
      site: "vagas",
      conversationId: "conv1",
      filters: { uf: ["SP"] },
      onFilters,
    });
    await chat.send("primeira");
    await chat.send("segunda", { filters: { uf: ["RJ"] } });

    const bodyOf = (call: number) =>
      JSON.parse(((fetchRaw.mock.calls[call] as unknown[])[1] as RequestInit).body as string);
    expect(bodyOf(0)).toMatchObject({ filters: { uf: ["SP"] }, includeFilters: true });
    expect(bodyOf(1)).toMatchObject({ filters: { uf: ["RJ"] }, includeFilters: true });
  });
});
