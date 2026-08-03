import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { SearchContent } from "../search-content";

/**
 * T868 / §LXVII.4 — this file mocks `useSearchStore`, which is why it stayed green
 * through the three T865-T867 bugs: the `data`-populated store below is the *legacy*
 * (`postNative: false`) shape, and since T710 made POST-native the default, no
 * production search produces it. It is still a valid legacy fixture and the T835
 * composition assertions here are cheap, so it stays — but the response shape that
 * actually ships is asserted in `search/__tests__/search-page.post-native.test.tsx`,
 * against the real provider. Do not add response-shape coverage here.
 */

// Shared spies + a mutable fake search store, hoisted so the vi.mock factories
// (which run before imports) can close over them.
const h = vi.hoisted(() => {
  const submitSearch = vi.fn();
  const ask = vi.fn();
  const store = {
    status: "success",
    data: { widget: { locales: [{ locale: "en" }], icon: "", spellCheck: false } },
    chat: null,
    documents: [{ id: "d1" }],
    params: { q: "test", sort: "relevance", _setlocale: "en" },
    inputValue: "test",
    error: null,
    setInputValue: vi.fn(),
    submitSearch,
    navigate: vi.fn(),
    showAll: vi.fn(),
    setSort: vi.fn(),
  };
  return { store, submitSearch, ask };
});

vi.mock("@viglet/turing-react-sdk", () => ({
  useSearchStore: () => h.store,
  useTuringAutoComplete: () => ({ suggestions: [], fetch: vi.fn(), clear: vi.fn() }),
  useTuringSortOptions: () => ({ sortOptions: [] }),
  fetchChatEnabled: () => Promise.resolve({ enabled: true }),
}));

// Child components → identifiable markers so we can assert what composes.
vi.mock("../ai-mode-chat", async () => {
  const React = await import("react");
  return {
    AiModeChat: React.forwardRef((_props: unknown, ref: React.Ref<{ ask: (q: string) => void }>) => {
      React.useImperativeHandle(ref, () => ({ ask: h.ask }));
      return React.createElement("div", { "data-testid": "ai-mode-chat" });
    }),
  };
});
vi.mock("../ai-chat-card", () => ({ AiChatCard: () => <div data-testid="ai-chat-card" /> }));
vi.mock("../search-results-panel", () => ({ SearchResultsPanel: () => <div data-testid="results-panel" /> }));
vi.mock("../search-empty", () => ({ SearchEmpty: () => <div data-testid="search-empty" /> }));
vi.mock("../search-footer", () => ({ SearchFooter: () => <div /> }));
vi.mock("../json-sheet", () => ({ JsonSheet: () => <div /> }));
vi.mock("../search-states", () => ({ SearchError: () => <div />, SearchLoading: () => <div /> }));
// The header exposes onSubmit via a button so we can drive the fused submit.
vi.mock("../search-header", () => ({
  SearchHeader: (props: { onSubmit?: () => void }) => (
    <button type="button" data-testid="submit" onClick={() => props.onSubmit?.()} />
  ),
}));

function renderAt(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <SearchContent siteName="site1" />
    </MemoryRouter>,
  );
}

describe("SearchContent — T835 answer-first fused page", () => {
  it("renders the answer panel ABOVE the still-mounted results when AI mode is on", async () => {
    renderAt("/?_aimode=true");

    // Answer panel appears once fetchChatEnabled resolves.
    expect(await screen.findByTestId("ai-mode-chat")).toBeInTheDocument();
    // The results panel stays mounted — the toggle is no longer exclusive.
    expect(screen.getByTestId("results-panel")).toBeInTheDocument();
    // The compact chat card gives way to the expanded answer panel.
    expect(screen.queryByTestId("ai-chat-card")).not.toBeInTheDocument();
  });

  it("collapses to the compact chat card while keeping results when AI mode is off", async () => {
    renderAt("/");

    expect(await screen.findByTestId("ai-chat-card")).toBeInTheDocument();
    expect(screen.getByTestId("results-panel")).toBeInTheDocument();
    expect(screen.queryByTestId("ai-mode-chat")).not.toBeInTheDocument();
  });

  it("fires the search AND the answer in parallel on submit in AI mode", async () => {
    renderAt("/?_aimode=true");
    await screen.findByTestId("ai-mode-chat");

    fireEvent.click(screen.getByTestId("submit"));

    expect(h.submitSearch).toHaveBeenCalledTimes(1);
    expect(h.ask).toHaveBeenCalledWith("test");
  });
});
