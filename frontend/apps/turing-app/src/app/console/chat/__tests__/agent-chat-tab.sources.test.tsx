import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import axios from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AgentChatTab } from "../components/agent-chat-tab";
import { makeAgent, makeSseFetch } from "./test-utils";

/**
 * T293 — RAG source chips + "why did you say this?" trace.
 *
 * Drives the real {@code useTuringChat} (agent mode) through AgentChatTab: a
 * turn whose SSE stream carries a {@code "sources"} event renders source chips
 * below the answer; expanding a chip reveals the cited passages.
 */
describe("AgentChatTab — RAG source chips (T293)", () => {
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
        modelLabel="openai · gpt-4o"
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

  it("renders source chips from a sources event and expands the trace", async () => {
    const sources = [
      { sourceId: "kb-1", title: "Refund Policy", url: "https://x/refunds", chunkIndex: 2, score: 0.91 },
      { sourceId: "kb-2", title: "Shipping FAQ", url: "https://x/shipping", chunkIndex: 0, score: 0.55 },
    ];
    const fetchMock = makeSseFetch([
      { type: "token", content: "You can get a refund within 30 days." },
      { type: "sources", content: JSON.stringify(sources) },
    ]);
    vi.stubGlobal("fetch", fetchMock);

    renderTab();
    await userEvent.type(screen.getByRole("textbox"), "refund policy?{Enter}");

    expect(await screen.findByText("You can get a refund within 30 days.")).toBeInTheDocument();
    const chip = await screen.findByText("Refund Policy");
    expect(chip).toBeInTheDocument();
    expect(screen.getByText("Shipping FAQ")).toBeInTheDocument();

    // Expand the trace → cited passage + deep link appear. (i18n is mocked in
    // tests to echo keys, so assert on data-driven content: the score and the
    // source href, plus the expanded region.)
    await userEvent.click(chip);
    await waitFor(() => expect(screen.getByRole("region")).toBeInTheDocument());
    expect(screen.getByText(/0\.91/)).toBeInTheDocument();
    const link = screen.getByRole("link");
    expect(link).toHaveAttribute("href", "https://x/refunds");
  });
});
