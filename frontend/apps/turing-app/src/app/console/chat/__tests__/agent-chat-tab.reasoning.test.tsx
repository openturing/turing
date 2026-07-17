import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import axios from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AgentChatTab } from "../components/agent-chat-tab";
import { makeAgent, makeSseFetch } from "./test-utils";

/**
 * T178 / §X.13.a — the "Why this answer" reasoning panel.
 *
 * Drives the real {@code useTuringChat} (agent mode) through AgentChatTab: a
 * turn whose SSE stream carries a {@code "reasoning"} event renders a collapsed
 * panel below the answer; clicking it reveals the reasoning summary.
 */
describe("AgentChatTab — reasoning summary panel (T178)", () => {
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

  it("renders the reasoning panel from a reasoning event and expands it", async () => {
    const fetchMock = makeSseFetch([
      { type: "token", content: "The capital of France is Paris." },
      { type: "reasoning", content: "Recalled that Paris is the capital city of France." },
    ]);
    vi.stubGlobal("fetch", fetchMock);

    renderTab();
    await userEvent.type(screen.getByRole("textbox"), "capital of France?{Enter}");

    expect(await screen.findByText("The capital of France is Paris.")).toBeInTheDocument();

    // The panel header is rendered collapsed (the summary text is hidden until
    // expanded). i18n is mocked to echo keys/defaults, so match the toggle by role.
    const toggle = await screen.findByRole("button", { name: /Why this answer/i });
    expect(
      screen.queryByText("Recalled that Paris is the capital city of France."),
    ).not.toBeInTheDocument();

    await userEvent.click(toggle);
    expect(
      await screen.findByText("Recalled that Paris is the capital city of France."),
    ).toBeInTheDocument();
  });
});
