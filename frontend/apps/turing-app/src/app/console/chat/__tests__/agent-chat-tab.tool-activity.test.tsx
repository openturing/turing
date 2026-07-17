import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import axios from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AgentChatTab } from "../components/agent-chat-tab";
import { makeAgent, makeSseFetch } from "./test-utils";

/**
 * T437 — live tool-call activity (TuringToolActivity).
 *
 * Drives the real {@code useTuringChat} (agent mode) through AgentChatTab: a
 * turn whose SSE stream carries {@code "tool_call"} events (start then end)
 * renders the tool-activity rows above the answer. i18n is mocked to echo keys,
 * so we assert on the tool name + the `data-status` the headless atom sets.
 */
describe("AgentChatTab — live tool activity (T437)", () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockResolvedValue({
      data: { conversationId: "conv-1", slots: {} },
    });
    document.cookie = "XSRF-TOKEN=test-csrf";
  });

  function renderTab() {
    return render(
      <AgentChatTab
        agent={makeAgent()}
        llmInstanceId="llm-1"
        contextWindow={128000}
        conversationId="conv-1"
        initialMessages={[]}
        activeFlows={[]}
        selectedFlowId="__auto__"
        onFlowChange={() => {}}
        compacting={false}
        onCompactRequested={() => {}}
        onResponseComplete={() => {}}
      />,
    );
  }

  it("renders a finished tool row from start+end tool_call events", async () => {
    const fetchMock = makeSseFetch([
      {
        type: "tool_call",
        content: JSON.stringify({
          callId: "c1",
          name: "search_knowledge_base",
          phase: "start",
          argsSummary: '{"query":"refunds"}',
        }),
      },
      {
        type: "tool_call",
        content: JSON.stringify({
          callId: "c1",
          name: "search_knowledge_base",
          phase: "end",
          status: "ok",
          durationMs: 42,
        }),
      },
      { type: "token", content: "You can get a refund within 30 days." },
    ]);
    vi.stubGlobal("fetch", fetchMock);

    renderTab();
    await userEvent.type(screen.getByRole("textbox"), "refund policy?{Enter}");

    expect(
      await screen.findByText("You can get a refund within 30 days."),
    ).toBeInTheDocument();
    // The tool name renders once the turn merges the start+end events by callId.
    expect(await screen.findByText("search_knowledge_base")).toBeInTheDocument();
    // The headless atom marks the finished row with data-status="done".
    const row = document.querySelector('[data-turing-tool-activity] [data-status="done"]');
    expect(row).not.toBeNull();
    expect(screen.getByText("42ms")).toBeInTheDocument();
  });
});
