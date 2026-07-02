import { render, screen } from "@testing-library/react";
import { createRef } from "react";
import { describe, expect, it } from "vitest";
import { ChatMessageList } from "../components/chat-message-list";
import type { ChatMessage } from "../chat.types";

/**
 * T490 / §X.19 — the admin chat renders the Google-mandated Search Suggestion
 * chips when a Gemini `google_search` grounded answer carries them.
 */
describe("ChatMessageList — Gemini Search Suggestions", () => {
  function renderList(message: ChatMessage) {
    return render(
      <ChatMessageList
        messages={[message]}
        loading={false}
        assistantName="Assistant"
        endRef={createRef<HTMLDivElement>()}
        onOptionClick={() => {}}
      />,
    );
  }

  const base: ChatMessage = {
    id: "a1",
    role: "assistant",
    content: "Paris is the capital of France.",
  };

  it("renders the Google-provided rendered chips HTML verbatim", () => {
    renderList({
      ...base,
      searchSuggestions: {
        renderedContent: '<div class="g-chip">capital of France</div>',
        queries: ["capital of France"],
      },
    });
    expect(document.querySelector(".g-chip")).not.toBeNull();
    expect(screen.getByText("capital of France")).toBeTruthy();
  });

  it("falls back to query chips when no rendered fragment is present", () => {
    renderList({
      ...base,
      searchSuggestions: { queries: ["who is the president of France"] },
    });
    const link = screen.getByText("who is the president of France").closest("a");
    expect(link).not.toBeNull();
    expect(link?.getAttribute("href")).toContain(
      "google.com/search?q=who%20is%20the%20president%20of%20France",
    );
  });

  it("renders nothing when there are no suggestions", () => {
    renderList(base);
    expect(screen.queryByText("chat.searchSuggestions.heading")).toBeNull();
  });
});
