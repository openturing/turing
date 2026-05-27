import "fake-indexeddb/auto";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import axios from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { saveSession } from "@/services/chat/chat-session.service";
import ChatPage from "../chat.page";
import { renderWithRouter } from "./test-utils";

// ── Admin service stubs (one agent + a default-agent setting + no flows) ──
vi.mock("@/services/agent/ai-agent.service", () => ({
  TurAIAgentService: class {
    query() {
      return Promise.resolve([
        { id: "agent-1", enabled: 1, title: "Support Agent", icon: null, llmInstances: [{ id: "llm-1" }] },
      ]);
    }
  },
}));
vi.mock("@/services/system/global-settings.service", () => ({
  TurGlobalSettingsService: class {
    query() {
      return Promise.resolve({ defaultAiAgentId: "agent-1" });
    }
  },
}));
vi.mock("@/services/agent/chat-flow.service", () => ({
  TurChatFlowService: class {
    query() {
      return Promise.resolve([]);
    }
  },
}));
// Avoid pulling i18n-driven date-locale resolution into the sidebar render.
vi.mock("@/hooks/use-date-locale", () => ({ useDateLocale: () => undefined }));

/**
 * T232.2 — session restore.
 *
 * Seeds the IndexedDB session store with a saved agent transcript, mounts the
 * page (which lists it in the sidebar via {@code useChatSession.loadSessions}),
 * then clicks it — exercising {@code handleRestoreSession} → the controlled
 * {@code conversationId} + {@code initialMessages} re-seed of AgentChatTab.
 */
describe("ChatPage — session restore (T232.2)", () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockImplementation((url: string) => {
      if (url === "/llm") {
        return Promise.resolve({
          data: [{ id: "llm-1", title: "GPT", enabled: 1, modelName: "gpt-4o", turLLMVendor: { id: "openai" } }],
        });
      }
      if (url.includes("/chat/context-info")) {
        return Promise.resolve({ data: { contextWindow: 128000, source: "config" } });
      }
      if (url.includes("/chat-slots")) {
        return Promise.resolve({ data: { conversationId: "x", slots: {} } });
      }
      return Promise.resolve({ data: {} });
    });
    vi.stubGlobal("fetch", vi.fn());
  });

  it("lists a saved session and restores its transcript on click", async () => {
    await saveSession({
      id: "sess-1",
      title: "Earlier chat",
      tab: "agent:agent-1",
      llmId: "llm-1",
      messages: [
        { id: "m1", role: "user", content: "Restored question" },
        { id: "m2", role: "assistant", content: "Restored answer" },
      ],
      createdAt: Date.now(),
      updatedAt: Date.now(),
    });

    renderWithRouter(<ChatPage />);

    // Page ready (agent + LLM resolved → AgentChatTab mounted with an input).
    await screen.findByRole("textbox");
    // Saved session shown in the sidebar.
    const sessionEntry = await screen.findByText("Earlier chat");

    await userEvent.click(sessionEntry);

    // Restored transcript renders in the chat pane.
    expect(await screen.findByText("Restored question")).toBeInTheDocument();
    expect(await screen.findByText("Restored answer")).toBeInTheDocument();
  });
});
