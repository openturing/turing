import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import axios from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AgentChatTab } from "../components/agent-chat-tab";
import { makeAgent, makeSseFetch } from "./test-utils";

/**
 * T232.1 — send + stream + chip-options.
 *
 * Drives the real {@code useTuringChat} (agent mode) through AgentChatTab:
 * type → submit → SSE tokens stream into the assistant bubble → the
 * "options" event renders chips → clicking a chip sends it as the next turn.
 * This is the core SDK ↔ admin protocol the T227 migration rewired.
 */
describe("AgentChatTab — send, stream, chip options (T232.1)", () => {
  beforeEach(() => {
    // scheduleAgent slot poll: no parked routine, so no banner interferes.
    vi.mocked(axios.get).mockResolvedValue({
      data: { conversationId: "conv-1", slots: {} },
    });
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

  it("streams the assistant reply and renders chip options", async () => {
    const fetchMock = makeSseFetch([
      { type: "token", content: "Hello! " },
      { type: "token", content: "How can I help?" },
      { type: "options", content: JSON.stringify(["Pricing", "Demo"]) },
    ]);
    vi.stubGlobal("fetch", fetchMock);

    renderTab();

    await userEvent.type(screen.getByRole("textbox"), "Hi there{Enter}");

    expect(await screen.findByText("Hello! How can I help?")).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Pricing" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Demo" })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("sends a chip's label as the next user message when clicked", async () => {
    const fetchMock = makeSseFetch([
      { type: "token", content: "Pick one:" },
      { type: "options", content: JSON.stringify(["Pricing", "Demo"]) },
    ]);
    vi.stubGlobal("fetch", fetchMock);

    renderTab();

    await userEvent.type(screen.getByRole("textbox"), "menu{Enter}");
    const chip = await screen.findByRole("button", { name: "Pricing" });

    await userEvent.click(chip);

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    const secondInit = fetchMock.mock.calls[1][1] as RequestInit;
    const body = JSON.parse(secondInit.body as string) as { messages: { content: string }[] };
    expect(body.messages.some((m) => m.content === "Pricing")).toBe(true);
  });
});
