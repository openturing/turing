import type { Meta, StoryObj } from "@storybook/react-vite";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  resolveDocsMock,
  creatureDocuments,
  creatureFacets,
} from "../__mocks__/fixtures";
import type { ResolvedDocument, TurFacetGroup } from "../../core/types";

/**
 * # useCoBrowseSearch
 *
 * Co-browsing turns the chat assistant into a second cursor on your **real**
 * search UI. The agent and the user look at the same result list and the same
 * facet checkboxes; when the user says *"filter to the fire creatures under
 * danger 7"*, the assistant doesn't just describe the answer — it **drives the
 * view**, flipping the actual facets and re-running the query through the same
 * search store the visible grid renders from. The user can also click facets
 * themselves, and the chat stays aware of the shared state.
 *
 * The hook returns a bag of `clientTools` whose names mirror the backend's
 * built-in co-browse tools (advertised when the agent has `coBrowseEnabled`).
 * You spread them into `useTuringChat`; each tool mutates the same
 * URL-synced search store you pass in — so co-browse changes also update the
 * browser URL and survive a refresh.
 *
 * ## Key Features
 * - `clientTools.set_search_query` — agent runs / replaces the query (resets to page 1)
 * - `clientTools.toggle_facet` — agent toggles a facet by **group name + visible label**, following the server-supplied `link` (same path `useTuringFacets` uses)
 * - `clientTools.clear_facets` — agent clears all active filters via the server's `cleanUpFacets` link
 * - `clientTools.set_sort` / `clientTools.set_page` — agent re-sorts / paginates
 * - **Single source of truth**: every tool writes the *same* store the UI reads, so the user always sees what the agent did — no out-of-band shadow state
 *
 * ## Usage
 * ```tsx
 * import { useTuringUrlSearch, useTuringChat, useCoBrowseSearch } from "@viglet/turing-react-sdk";
 *
 * function CoBrowsePage() {
 *   const store = useTuringUrlSearch();          // URL-synced search store
 *   const coBrowse = useCoBrowseSearch(store);   // → { clientTools }
 *   const chat = useTuringChat({
 *     agent: "bestiary-guide",
 *     clientTools: coBrowse.clientTools,         // agent can now drive the view
 *   });
 *
 *   return (
 *     <div style={{ display: "flex" }}>
 *       <ChatPanel chat={chat} />
 *       <SearchResults store={store} />          // renders from the SAME store
 *     </div>
 *   );
 * }
 * ```
 *
 * ## When to use
 * - **Guided shopping / catalogs**: the assistant narrows a large result set for
 *   the user instead of pasting a wall of links — the user keeps the live,
 *   clickable grid.
 * - **Support & onboarding**: "let me filter that for you" without a screen-share;
 *   the agent's actions are visible and reversible by the user.
 * - **Accessibility**: natural-language control of facets and sort for users who
 *   find checkbox forests hard to navigate.
 *
 * ## About this story
 * Fully self-contained — **no real hook, Provider, or network**. A tiny scripted
 * "agent" emits the same actions the real `clientTools` expose (`toggle_facet`,
 * `set_search_query`, `clear_facets`) against a local mirror of the search store
 * built from the Mythical Creatures fixture. Chat is on the left; the live result
 * grid + facets are on the right. When the agent drives the view, the right pane
 * flashes a brief highlight so the synchronization is visible. You can also click
 * facets yourself — the chat acknowledges the user-initiated change.
 */

/* ── Local mirror of the search store the real hook mutates ── */

interface ActiveFilter {
  group: string;
  value: string;
}

/** A scripted agent action — mirrors a real `clientTools` call. */
interface DriveAction {
  tool: "toggle_facet" | "set_search_query" | "clear_facets";
  field?: string;
  value?: string;
  query?: string;
  /** What the assistant "says" while performing the action. */
  say: string;
}

interface ChatTurn {
  role: "user" | "assistant";
  text: string;
  /** True when this assistant turn drove the shared view. */
  drove?: boolean;
}

/** One scripted exchange: a user prompt → assistant reply that drives the view. */
interface ScriptedExchange {
  user: string;
  reply: string;
  actions: DriveAction[];
}

const SCRIPT: ReadonlyArray<ScriptedExchange> = [
  {
    user: "Show me only the fire creatures",
    reply: "Filtering the bestiary to creatures with the Fire element for you.",
    actions: [
      { tool: "toggle_facet", field: "element", value: "Fire", say: "toggle_facet(element=Fire)" },
    ],
  },
  {
    user: "Just the Greek ones, please",
    reply: "Adding an Origin = Greek filter on top of that.",
    actions: [
      { tool: "toggle_facet", field: "origin", value: "Greek", say: "toggle_facet(origin=Greek)" },
    ],
  },
  {
    user: "Actually, search for 'guardian' instead and clear the filters",
    reply: "Clearing all filters and running a search for “guardian”.",
    actions: [
      { tool: "clear_facets", say: "clear_facets()" },
      { tool: "set_search_query", query: "guardian", say: "set_search_query(“guardian”)" },
    ],
  },
];

const GRADIENT = "linear-gradient(135deg, #2563eb 0%, #4f46e5 100%)";

/* ── Filtering helpers (simulate the store's data.results after a query) ── */

function matchesFilters(doc: ResolvedDocument, filters: ActiveFilter[]): boolean {
  return filters.every((f) => {
    const fieldValue = doc.raw.fields[f.group];
    if (Array.isArray(fieldValue)) return fieldValue.includes(f.value);
    return fieldValue === f.value;
  });
}

function matchesQuery(doc: ResolvedDocument, query: string): boolean {
  if (!query || query === "*") return true;
  const hay = `${doc.title} ${doc.description}`.toLowerCase();
  return query
    .toLowerCase()
    .split(/\s+/)
    .every((token) => hay.includes(token));
}

function CoBrowseDemo({
  facetGroups,
  documents,
  theme,
}: {
  facetGroups: TurFacetGroup[];
  documents: ResolvedDocument[];
  theme: "light" | "dark";
}) {
  const isDark = theme === "dark";

  // ── Shared search store (the ONE source of truth both panes read) ──
  const [query, setQuery] = useState("*");
  const [filters, setFilters] = useState<ActiveFilter[]>([]);
  const [turns, setTurns] = useState<ChatTurn[]>([
    {
      role: "assistant",
      text: "Hi! Ask me to filter the bestiary and watch the grid on the right update in sync. 🐉",
    },
  ]);
  const [stepIdx, setStepIdx] = useState(0);
  const [driving, setDriving] = useState(false);
  // Highlight pulse on the right pane when the agent drives the view.
  const [agentPulse, setAgentPulse] = useState(false);
  const pulseTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const filteredDocs = useMemo(
    () => documents.filter((d) => matchesFilters(d, filters) && matchesQuery(d, query)),
    [documents, filters, query],
  );

  const flashAgent = useCallback(() => {
    setAgentPulse(true);
    if (pulseTimer.current) clearTimeout(pulseTimer.current);
    pulseTimer.current = setTimeout(() => setAgentPulse(false), 900);
  }, []);

  useEffect(() => {
    return () => {
      if (pulseTimer.current) clearTimeout(pulseTimer.current);
    };
  }, []);

  // ── Apply one scripted agent action against the shared store ──
  const applyAction = useCallback((action: DriveAction) => {
    switch (action.tool) {
      case "toggle_facet":
        setFilters((prev) => {
          const exists = prev.some((f) => f.group === action.field && f.value === action.value);
          if (exists) return prev.filter((f) => !(f.group === action.field && f.value === action.value));
          return [...prev, { group: action.field as string, value: action.value as string }];
        });
        break;
      case "clear_facets":
        setFilters([]);
        break;
      case "set_search_query":
        setQuery((action.query ?? "*").trim() || "*");
        break;
    }
  }, []);

  // ── The "agent" runs the next scripted exchange ──
  const runNextStep = useCallback(() => {
    if (driving || stepIdx >= SCRIPT.length) return;
    const exchange = SCRIPT[stepIdx];
    setDriving(true);

    // 1) user turn appears
    setTurns((prev) => [...prev, { role: "user", text: exchange.user }]);

    // 2) assistant replies + drives the view after a short "thinking" beat
    setTimeout(() => {
      setTurns((prev) => [...prev, { role: "assistant", text: exchange.reply, drove: true }]);
      exchange.actions.forEach((action, i) => {
        setTimeout(() => {
          applyAction(action);
          flashAgent();
        }, 220 * (i + 1));
      });
      setTimeout(() => {
        setStepIdx((i) => i + 1);
        setDriving(false);
      }, 220 * (exchange.actions.length + 1));
    }, 500);
  }, [driving, stepIdx, applyAction, flashAgent]);

  // ── User-initiated facet click — chat acknowledges the shared change ──
  const userToggleFacet = useCallback((group: string, value: string) => {
    applyAction({ tool: "toggle_facet", field: group, value, say: "" });
    setTurns((prev) => [
      ...prev,
      { role: "user", text: `(clicked ${group}: ${value})` },
      {
        role: "assistant",
        text: `Got it — I see you toggled ${value} under ${group}. We're looking at the same results.`,
      },
    ]);
  }, [applyAction]);

  const resetDemo = useCallback(() => {
    setFilters([]);
    setQuery("*");
    setStepIdx(0);
    setDriving(false);
    setTurns([
      {
        role: "assistant",
        text: "Reset. Ask me to filter the bestiary again. 🐉",
      },
    ]);
  }, []);

  const c = {
    bg: isDark ? "#0b1120" : "#f8fafc",
    panel: isDark ? "#0f172a" : "#ffffff",
    border: isDark ? "#1e293b" : "#e2e8f0",
    text: isDark ? "#e2e8f0" : "#0f172a",
    muted: isDark ? "#94a3b8" : "#64748b",
    chip: isDark ? "#1e293b" : "#f1f5f9",
    accent: isDark ? "#60a5fa" : "#4f46e5",
  };

  return (
    <div
      style={{
        display: "flex",
        height: "100vh",
        fontFamily: "system-ui, sans-serif",
        background: c.bg,
        color: c.text,
      }}
    >
      {/* ════════ LEFT: chat ════════ */}
      <div
        style={{
          width: "38%",
          minWidth: 320,
          display: "flex",
          flexDirection: "column",
          borderRight: `1px solid ${c.border}`,
          background: c.panel,
        }}
      >
        <div
          style={{
            padding: "14px 18px",
            background: GRADIENT,
            color: "white",
            fontWeight: 700,
            fontSize: 14,
            display: "flex",
            alignItems: "center",
            gap: 8,
          }}
        >
          <span style={{ fontSize: 18 }}>{"🐉👥"}</span>
          Bestiary Guide
          <span style={{ marginLeft: "auto", fontSize: 11, fontWeight: 500, opacity: 0.85 }}>
            co-browse on
          </span>
        </div>

        <div style={{ flex: 1, overflowY: "auto", padding: "16px", display: "flex", flexDirection: "column", gap: 10 }}>
          {turns.map((turn, i) => (
            <div
              key={i}
              style={{
                alignSelf: turn.role === "user" ? "flex-end" : "flex-start",
                maxWidth: "85%",
                padding: "9px 13px",
                borderRadius: 14,
                fontSize: 13,
                lineHeight: 1.45,
                background:
                  turn.role === "user"
                    ? GRADIENT
                    : isDark
                      ? "#1e293b"
                      : "#f1f5f9",
                color: turn.role === "user" ? "white" : c.text,
                border: turn.role === "assistant" && turn.drove ? `1px solid ${c.accent}` : "none",
              }}
            >
              {turn.text}
              {turn.drove && (
                <div style={{ marginTop: 5, fontSize: 10, opacity: 0.7, fontStyle: "italic" }}>
                  {"⚙️ drove the shared view"}
                </div>
              )}
            </div>
          ))}
          {driving && (
            <div style={{ alignSelf: "flex-start", fontSize: 12, color: c.muted }}>
              {"… driving the view"}
            </div>
          )}
        </div>

        <div style={{ padding: "12px 16px", borderTop: `1px solid ${c.border}`, display: "flex", gap: 8 }}>
          <button
            type="button"
            onClick={runNextStep}
            disabled={driving || stepIdx >= SCRIPT.length}
            style={{
              flex: 1,
              padding: "10px 14px",
              borderRadius: 10,
              border: "none",
              background: driving || stepIdx >= SCRIPT.length ? "#94a3b8" : GRADIENT,
              color: "white",
              fontWeight: 600,
              fontSize: 13,
              cursor: driving || stepIdx >= SCRIPT.length ? "not-allowed" : "pointer",
            }}
          >
            {stepIdx >= SCRIPT.length
              ? "✓ Script complete"
              : `▶ Send: “${SCRIPT[stepIdx].user.slice(0, 28)}${SCRIPT[stepIdx].user.length > 28 ? "…" : ""}”`}
          </button>
          <button
            type="button"
            onClick={resetDemo}
            style={{
              padding: "10px 14px",
              borderRadius: 10,
              border: `1px solid ${c.border}`,
              background: "transparent",
              color: c.muted,
              fontSize: 13,
              cursor: "pointer",
            }}
          >
            {"↻"}
          </button>
        </div>
      </div>

      {/* ════════ RIGHT: live shared search view ════════ */}
      <div
        style={{
          flex: 1,
          display: "flex",
          flexDirection: "column",
          minWidth: 0,
          outline: agentPulse ? `3px solid ${c.accent}` : "3px solid transparent",
          outlineOffset: "-3px",
          transition: "outline-color 0.4s ease",
        }}
      >
        {/* Query + active-filter bar (the live store state) */}
        <div
          style={{
            padding: "12px 20px",
            borderBottom: `1px solid ${c.border}`,
            display: "flex",
            alignItems: "center",
            gap: 10,
            flexWrap: "wrap",
            background: agentPulse ? (isDark ? "#13203a" : "#eef2ff") : c.panel,
            transition: "background 0.4s ease",
          }}
        >
          <span style={{ fontSize: 12, color: c.muted }}>query:</span>
          <code
            style={{
              padding: "2px 8px",
              borderRadius: 6,
              background: c.chip,
              fontSize: 12,
              color: c.accent,
            }}
          >
            {query}
          </code>
          {filters.length > 0 && (
            <>
              <span style={{ fontSize: 12, color: c.muted }}>filters:</span>
              {filters.map((f) => (
                <span
                  key={`${f.group}:${f.value}`}
                  style={{
                    fontSize: 11,
                    padding: "2px 8px",
                    borderRadius: 999,
                    background: isDark ? "rgba(96,165,250,0.18)" : "rgba(79,70,229,0.1)",
                    color: c.accent,
                    fontWeight: 600,
                  }}
                >
                  {f.group}: {f.value}
                </span>
              ))}
            </>
          )}
          <span style={{ marginLeft: "auto", fontSize: 12, color: c.muted }}>
            <strong style={{ color: c.text }}>{filteredDocs.length}</strong> results
          </span>
        </div>

        <div style={{ flex: 1, display: "flex", minHeight: 0 }}>
          {/* Facet rail — clickable by the user, also driven by the agent */}
          <aside
            style={{
              width: 190,
              flexShrink: 0,
              padding: "16px",
              borderRight: `1px solid ${c.border}`,
              overflowY: "auto",
            }}
          >
            <div style={{ fontSize: 11, fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.08em", color: c.muted, marginBottom: 10 }}>
              Filters
            </div>
            {facetGroups.map((group) => (
              <div key={group.name} style={{ marginBottom: 14 }}>
                <div style={{ fontSize: 10, fontWeight: 700, textTransform: "uppercase", color: c.muted, marginBottom: 5 }}>
                  {group.label.text}
                </div>
                {group.facets.map((facet) => {
                  const selected = filters.some((f) => f.group === group.name && f.value === facet.label);
                  return (
                    <button
                      key={facet.label}
                      type="button"
                      onClick={() => userToggleFacet(group.name, facet.label)}
                      style={{
                        display: "flex",
                        width: "100%",
                        justifyContent: "space-between",
                        alignItems: "center",
                        padding: "5px 8px",
                        marginBottom: 2,
                        borderRadius: 6,
                        border: "none",
                        cursor: "pointer",
                        textAlign: "left",
                        fontSize: 12.5,
                        background: selected ? (isDark ? "rgba(96,165,250,0.15)" : "rgba(79,70,229,0.08)") : "transparent",
                        color: selected ? c.accent : c.muted,
                        fontWeight: selected ? 600 : 400,
                      }}
                    >
                      <span>{selected ? "✓ " : ""}{facet.label}</span>
                      <span style={{ fontSize: 10, padding: "1px 6px", borderRadius: 8, background: c.chip }}>
                        {facet.count}
                      </span>
                    </button>
                  );
                })}
              </div>
            ))}
          </aside>

          {/* Live result grid */}
          <div style={{ flex: 1, overflowY: "auto", padding: "18px" }}>
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))",
                gap: 14,
              }}
            >
              {filteredDocs.map((doc) => (
                <div
                  key={doc.url}
                  style={{
                    borderRadius: 12,
                    overflow: "hidden",
                    border: `1px solid ${c.border}`,
                    background: c.panel,
                  }}
                >
                  <img
                    src={doc.image}
                    alt={doc.title}
                    style={{ width: "100%", height: 110, objectFit: "cover", display: "block" }}
                  />
                  <div style={{ padding: "10px 12px" }}>
                    <div style={{ fontWeight: 600, fontSize: 13.5 }}>{doc.title}</div>
                    <div style={{ fontSize: 11.5, color: c.muted, marginTop: 3, lineHeight: 1.4 }}>
                      {doc.description.slice(0, 72)}…
                    </div>
                  </div>
                </div>
              ))}
            </div>
            {filteredDocs.length === 0 && (
              <div style={{ textAlign: "center", padding: 48, color: c.muted, fontSize: 14 }}>
                No creatures match the shared view.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

const meta: Meta<typeof CoBrowseDemo> = {
  title: "Platform/useCoBrowseSearch",
  component: CoBrowseDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "fullscreen",
    docs: {
      description: {
        component:
          "Co-browse: the chat assistant and the user share one synchronized search view. The agent drives the **real** search store (query, facets, sort, page) through `clientTools` that mirror the backend's built-in co-browse tools, so the result grid the user sees updates in sync — and user clicks stay visible to the chat. This story is a self-contained simulation (no Provider/network): a scripted agent emits `toggle_facet` / `set_search_query` / `clear_facets` against a local mirror of the store; the right pane flashes when the agent drives it.",
      },
    },
  },
  argTypes: {
    facetGroups: {
      description: "Facet groups rendered in the shared rail (the agent toggles these by group name + label).",
      control: false,
    },
    documents: {
      description: "Resolved documents backing the live result grid both panes read from.",
      control: false,
    },
    theme: {
      description: "Light or dark presentation of the split co-browse surface.",
      control: { type: "inline-radio" },
      options: ["light", "dark"],
    },
  },
};

export default meta;
type Story = StoryObj<typeof CoBrowseDemo>;

export const AgentDrivesTheView: Story = {
  name: "🐉👥 Agent drives the shared view",
  args: {
    facetGroups: creatureFacets,
    documents: resolveDocsMock(creatureDocuments),
    theme: "light",
  },
};

export const UserAndAgentTogether: Story = {
  name: "👥 User clicks, chat stays in sync",
  args: {
    facetGroups: creatureFacets,
    documents: resolveDocsMock(creatureDocuments),
    theme: "light",
  },
};

export const DarkCoBrowse: Story = {
  name: "🌑 Co-browse (dark)",
  args: {
    facetGroups: creatureFacets,
    documents: resolveDocsMock(creatureDocuments),
    theme: "dark",
  },
};
