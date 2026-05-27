import { render, screen, waitForElementToBeRemoved } from "@testing-library/react";
import axios from "axios";
import { describe, expect, it, vi } from "vitest";
import { AgentChatTab } from "../components/agent-chat-tab";
import { makeAgent } from "./test-utils";

/**
 * T232.3 — scheduleAgent "Aguardando rotina…" banner.
 *
 * AgentChatTab owns {@code useTuringScheduleAgentWaiting}, which polls
 * {@code GET /v2/ai-agent/{id}/chat-slots} every 2s. When a
 * {@code __scheduleAgent_pending_*} marker slot is present the banner shows;
 * when the routine completes (marker cleared) the next poll hides it.
 */
describe("AgentChatTab — scheduleAgent waiting banner (T232.3)", () => {
  function renderTab() {
    return render(
      <AgentChatTab
        agent={makeAgent()}
        llmInstanceId="llm-1"
        modelLabel="openai · gpt-4o"
        contextWindow={128000}
        conversationId="conv-3"
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

  it(
    "shows the banner while a routine marker is parked and hides it once cleared",
    async () => {
      vi.stubGlobal("fetch", vi.fn()); // no chat send in this test
      // First poll: a routine is parked on a scheduleAgent node.
      vi.mocked(axios.get).mockResolvedValue({
        data: {
          conversationId: "conv-3",
          slots: { __scheduleAgent_pending_node1: "routine-1" },
        },
      });

      renderTab();

      expect(await screen.findByText("Aguardando rotina…")).toBeInTheDocument();

      // Routine completes → marker gone on the next 2s poll.
      vi.mocked(axios.get).mockResolvedValue({
        data: { conversationId: "conv-3", slots: {} },
      });

      await waitForElementToBeRemoved(
        () => screen.queryByText("Aguardando rotina…"),
        { timeout: 4000 },
      );
    },
    10000,
  );
});
