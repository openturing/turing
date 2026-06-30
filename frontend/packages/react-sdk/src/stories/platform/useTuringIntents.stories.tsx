import type { Meta, StoryObj } from "@storybook/react-vite";
import { useMemo, useState } from "react";

/**
 * # useTuringIntents
 *
 * Loads the curated list of **enabled intents** for a site's AI agent and
 * exposes them so the UI can surface *intent-based shortcuts*. An intent maps
 * a user goal — "track a launch", "contact mission control", "see Mars rovers"
 * — to a small set of **actions** (`{ label, prompt }`), which the host app
 * renders as buttons or cards. Clicking an action fires its `prompt` into chat
 * or search, turning a free-text query into a guided next step.
 *
 * The hook itself is a thin read: it fetches the catalog once per `site`,
 * sorts by `sortOrder`, and re-exposes `intents`, `loading`, `error`, and a
 * `refresh()`. **Matching a typed query to an intent is the caller's job** —
 * this story shows the common pattern (trigger-phrase matching) and the
 * fall-through to normal search when nothing matches.
 *
 * ## Key Features
 * - `intents`: enabled `TurIntent[]`, already sorted by `sortOrder`
 * - Each intent carries `title`, optional `description` / `icon`, and an
 *   ordered `actions` array of `{ label, prompt }`
 * - `loading` / `error`: request lifecycle for skeletons + retry UI
 * - `refresh()`: re-fetch the catalog (e.g. after an admin edits intents)
 *
 * ## Usage
 * ```tsx
 * const { intents, loading, error, refresh } = useTuringIntents();
 *
 * // Render every action as a quick-shortcut chip
 * const shortcuts = intents.flatMap((i) => i.actions);
 * return shortcuts.map((a) => (
 *   <button key={a.id ?? a.label} onClick={() => send(a.prompt)}>
 *     {a.label}
 *   </button>
 * ));
 * ```
 *
 * ## When to use
 * - **Intent shortcuts above search**: detect the user's goal from their query
 *   and surface action buttons ("Track a launch", "Open contact form") before
 *   they scroll the results.
 * - **Empty-state / composer chips**: show the top intents as starter prompts
 *   when the search box or chat is still empty.
 * - **Deflection**: route "contact" / "support" style queries to a form or a
 *   human-handoff action instead of returning low-signal documents.
 *
 * ## About this story
 * Fully self-contained — **no real hook, Provider, or network**. It ships an
 * inline **Space Missions** intent catalog (trigger phrases → intent +
 * actions). As you type and submit a query, the demo matches it against the
 * catalog: on a hit it renders the intent's action card(s) above a stub result
 * list; on a miss it shows the "no intent matched → normal search" path.
 */

/* ── Local shapes (mirror `TurIntent` / `TurIntentAction` from ../../core/types) ── */

interface DemoIntentAction {
  id?: string;
  label: string;
  /** The prompt fired into chat/search when the action is clicked. */
  prompt: string;
  sortOrder?: number;
}

interface DemoIntent {
  id?: string;
  title: string;
  description?: string;
  /** Emoji used as the card glyph (real `TurIntent.icon` is an Iconify id). */
  icon?: string;
  /** 1 = enabled, 0 = disabled */
  enabled: number;
  sortOrder?: number;
  actions: DemoIntentAction[];
  /** Story-only: phrases that route a query to this intent. */
  triggers: string[];
}

const GRADIENT = "linear-gradient(135deg, #2563eb 0%, #4f46e5 100%)";

/* ── Inline Space Missions intent catalog ── */
const SPACE_INTENTS: DemoIntent[] = [
  {
    id: "track-launch",
    title: "Track a launch",
    description: "Live countdown + telemetry for upcoming and ongoing missions.",
    icon: "🚀",
    enabled: 1,
    sortOrder: 1,
    triggers: ["track a launch", "track launch", "launch countdown", "next launch", "liftoff"],
    actions: [
      { id: "open-tracker", label: "Open launch tracker", prompt: "/launches/upcoming" },
      { id: "set-alert", label: "Set T-0 alert", prompt: "Notify me 10 minutes before T-0" },
    ],
  },
  {
    id: "contact-control",
    title: "Contact mission control",
    description: "Reach a flight controller or open a support ticket.",
    icon: "📡",
    enabled: 1,
    sortOrder: 2,
    triggers: ["contact mission control", "contact control", "talk to support", "help", "human"],
    actions: [
      { id: "open-form", label: "Open contact form", prompt: "/support/new-ticket" },
      { id: "live-chat", label: "Chat with a controller", prompt: "Start a live chat with mission control" },
    ],
  },
  {
    id: "mars-rovers",
    title: "See Mars rovers",
    description: "Rover gallery, latest images, and mission status.",
    icon: "🤖",
    enabled: 1,
    sortOrder: 3,
    triggers: ["see mars rovers", "mars rovers", "mars rover", "rover", "perseverance", "curiosity"],
    actions: [
      { id: "rover-gallery", label: "Rover image gallery", prompt: "/mars/rovers/gallery" },
      { id: "rover-status", label: "Current rover status", prompt: "What is Perseverance doing right now?" },
    ],
  },
];

/* ── Stub fall-through search results (when no intent matches) ── */
const STUB_RESULTS = [
  "Apollo 11 — first crewed Moon landing (1969)",
  "Voyager 1 — interstellar probe, launched 1977",
  "James Webb Space Telescope — deep-field infrared imaging",
];

function matchIntent(query: string, intents: DemoIntent[]): DemoIntent | null {
  const q = query.trim().toLowerCase();
  if (!q) return null;
  // Highest-priority enabled intent whose trigger phrase appears in the query.
  return (
    intents
      .filter((i) => i.enabled === 1)
      .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
      .find((i) => i.triggers.some((t) => q.includes(t) || t.includes(q))) ?? null
  );
}

function IntentsDemo({
  dark,
  showResultsOnMatch,
}: {
  dark: boolean;
  showResultsOnMatch: boolean;
}) {
  const [query, setQuery] = useState("");
  const [submitted, setSubmitted] = useState("");
  const [lastFired, setLastFired] = useState<string | null>(null);

  // Sorted, enabled catalog — mirrors what `useTuringIntents().intents` returns.
  const intents = useMemo(
    () =>
      SPACE_INTENTS.filter((i) => i.enabled === 1).sort(
        (a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0),
      ),
    [],
  );

  const matched = useMemo(() => matchIntent(submitted, intents), [submitted, intents]);
  const hasQuery = submitted.trim().length > 0;

  const bg = dark ? "#0a0a0f" : "#ffffff";
  const cardBg = dark ? "#16161f" : "#ffffff";
  const border = dark ? "#1e1e2e" : "#e2e8f0";
  const text = dark ? "#e2e8f0" : "#0f172a";
  const muted = dark ? "#64748b" : "#94a3b8";

  function submit(value: string) {
    setSubmitted(value);
    setLastFired(null);
  }

  return (
    <div
      style={{
        fontFamily: "system-ui, sans-serif",
        width: "560px",
        background: bg,
        color: text,
        padding: "22px",
        borderRadius: "16px",
        border: `1px solid ${border}`,
      }}
    >
      {/* Header */}
      <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "4px" }}>
        <span
          style={{
            width: 34,
            height: 34,
            borderRadius: "9px",
            background: GRADIENT,
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: "18px",
          }}
        >
          🎯
        </span>
        <div>
          <div style={{ fontWeight: 700, fontSize: "16px" }}>Space Missions</div>
          <div style={{ fontSize: "12px", color: muted }}>Intent-based shortcuts</div>
        </div>
      </div>

      <p style={{ fontSize: "12px", color: muted, margin: "10px 0 14px" }}>
        Try: <em>&quot;track a launch&quot;</em>, <em>&quot;contact mission control&quot;</em>,{" "}
        <em>&quot;see Mars rovers&quot;</em> — or something unrelated (e.g. <em>&quot;black holes&quot;</em>) to see the fall-through.
      </p>

      {/* Search composer */}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit(query);
        }}
        style={{
          display: "flex",
          gap: "8px",
          alignItems: "center",
          border: `2px solid ${border}`,
          borderRadius: "12px",
          padding: "8px 12px",
          background: cardBg,
          marginBottom: "16px",
        }}
      >
        <span style={{ fontSize: "16px" }}>🔍</span>
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search Space Missions..."
          style={{
            flex: 1,
            border: "none",
            outline: "none",
            fontSize: "15px",
            background: "transparent",
            color: text,
          }}
        />
        <button
          type="submit"
          style={{
            border: "none",
            borderRadius: "8px",
            background: GRADIENT,
            color: "white",
            fontSize: "13px",
            fontWeight: 600,
            padding: "8px 16px",
            cursor: "pointer",
          }}
        >
          Search
        </button>
      </form>

      {/* Intent match → action card(s) */}
      {matched && (
        <div
          style={{
            border: `1px solid ${border}`,
            borderRadius: "14px",
            overflow: "hidden",
            marginBottom: "16px",
            background: cardBg,
          }}
        >
          <div
            style={{
              background: GRADIENT,
              color: "white",
              padding: "12px 16px",
              display: "flex",
              alignItems: "center",
              gap: "10px",
            }}
          >
            <span style={{ fontSize: "22px" }}>{matched.icon}</span>
            <div>
              <div style={{ fontWeight: 700, fontSize: "15px" }}>{matched.title}</div>
              {matched.description && (
                <div style={{ fontSize: "12px", opacity: 0.9 }}>{matched.description}</div>
              )}
            </div>
            <span
              style={{
                marginLeft: "auto",
                fontSize: "10px",
                fontWeight: 600,
                textTransform: "uppercase",
                letterSpacing: "0.06em",
                background: "rgba(255,255,255,0.18)",
                borderRadius: "999px",
                padding: "3px 9px",
              }}
            >
              Intent matched
            </span>
          </div>
          <div style={{ display: "flex", gap: "8px", flexWrap: "wrap", padding: "14px 16px" }}>
            {matched.actions.map((a) => (
              <button
                key={a.id ?? a.label}
                type="button"
                onClick={() => setLastFired(a.prompt)}
                style={{
                  border: `1px solid ${border}`,
                  borderRadius: "10px",
                  background: dark ? "#1e293b" : "#f8fafc",
                  color: text,
                  fontSize: "13px",
                  fontWeight: 600,
                  padding: "9px 14px",
                  cursor: "pointer",
                }}
              >
                {a.label}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Fired-action feedback */}
      {lastFired && (
        <div
          style={{
            fontSize: "12px",
            color: dark ? "#a5b4fc" : "#4f46e5",
            background: dark ? "#1e1b4b" : "#eef2ff",
            border: `1px solid ${dark ? "#312e81" : "#c7d2fe"}`,
            borderRadius: "10px",
            padding: "10px 12px",
            marginBottom: "16px",
          }}
        >
          ▶ Fired action prompt: <code>{lastFired}</code>
        </div>
      )}

      {/* Fall-through: no intent matched → normal search results */}
      {hasQuery && !matched && (
        <div>
          <div style={{ fontSize: "11px", color: muted, marginBottom: "8px", textTransform: "uppercase", letterSpacing: "0.06em" }}>
            No intent matched — showing search results
          </div>
          {STUB_RESULTS.map((r) => (
            <div
              key={r}
              style={{
                padding: "10px 12px",
                border: `1px solid ${border}`,
                borderRadius: "10px",
                marginBottom: "8px",
                fontSize: "14px",
                background: cardBg,
              }}
            >
              📄 {r}
            </div>
          ))}
        </div>
      )}

      {/* Match results stub also shown when an intent matched, if enabled */}
      {hasQuery && matched && showResultsOnMatch && (
        <div>
          <div style={{ fontSize: "11px", color: muted, marginBottom: "8px", textTransform: "uppercase", letterSpacing: "0.06em" }}>
            Plus regular results
          </div>
          {STUB_RESULTS.slice(0, 2).map((r) => (
            <div
              key={r}
              style={{
                padding: "10px 12px",
                border: `1px solid ${border}`,
                borderRadius: "10px",
                marginBottom: "8px",
                fontSize: "14px",
                background: cardBg,
              }}
            >
              📄 {r}
            </div>
          ))}
        </div>
      )}

      {/* Catalog preview — what useTuringIntents().intents holds */}
      <div
        style={{
          marginTop: "8px",
          paddingTop: "14px",
          borderTop: `1px dashed ${border}`,
          fontSize: "11px",
          color: muted,
        }}
      >
        <strong>Catalog ({intents.length} enabled intents):</strong>{" "}
        {intents.map((i) => i.title).join(" · ")}
      </div>
    </div>
  );
}

const meta: Meta<typeof IntentsDemo> = {
  title: "Platform/useTuringIntents",
  component: IntentsDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Self-contained simulation of `useTuringIntents`. An inline Space Missions intent catalog maps trigger phrases to intents + actions. Submit a query: matching intents surface action cards above the results, while unmatched queries fall through to a normal search result list.",
      },
    },
  },
  argTypes: {
    dark: {
      description: "Render the dark variant (uses the Viglet dark surface palette).",
      control: { type: "boolean" },
    },
    showResultsOnMatch: {
      description: "Also show regular search results below the action card when an intent matches.",
      control: { type: "boolean" },
    },
  },
};

export default meta;
type Story = StoryObj<typeof IntentsDemo>;

export const Default: Story = {
  name: "🚀 Intent shortcuts (light)",
  args: { dark: false, showResultsOnMatch: false },
};

export const WithResults: Story = {
  name: "🎯 Action card + results",
  args: { dark: false, showResultsOnMatch: true },
};

export const DarkMode: Story = {
  name: "🌙 Dark mode",
  args: { dark: true, showResultsOnMatch: false },
};
