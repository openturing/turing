import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import axios from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AgentChatTab } from "../components/agent-chat-tab";
import { makeAgent, makeSseFetch } from "./test-utils";

/**
 * F.10 / T172 — "rephrase shorter" action.
 *
 * Drives the real {@code useTuringChat} (agent mode): a turn produces an answer,
 * the operator clicks "Shorter", and the backend rewrite replaces the bubble
 * content in place (a local override, leaving the SDK transcript untouched).
 */
describe("AgentChatTab — rephrase shorter (T172)", () => {
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

  it("rewrites the answer in place when Shorter is clicked", async () => {
    const fetchMock = makeSseFetch([
      { type: "token", content: "You can get a refund within thirty days of purchase." },
    ]);
    vi.stubGlobal("fetch", fetchMock);
    vi.mocked(axios.post).mockResolvedValue({
      data: { success: true, rephrased: "Refunds: 30 days.", usedPrediction: true },
    });

    renderTab();
    await userEvent.type(screen.getByRole("textbox"), "refund policy?{Enter}");

    expect(
      await screen.findByText("You can get a refund within thirty days of purchase."),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Rephrase shorter" }));

    // The backend was asked to rewrite the original answer for this agent.
    await waitFor(() =>
      expect(axios.post).toHaveBeenCalledWith(
        "/ai-agent/agent-1/chat-rephrase",
        expect.objectContaining({
          answer: "You can get a refund within thirty days of purchase.",
          style: "shorter",
          llmInstanceId: "llm-1",
        }),
      ),
    );
    // The bubble now shows the rewrite, not the original.
    expect(await screen.findByText("Refunds: 30 days.")).toBeInTheDocument();
    expect(
      screen.queryByText("You can get a refund within thirty days of purchase."),
    ).not.toBeInTheDocument();
  });
});
