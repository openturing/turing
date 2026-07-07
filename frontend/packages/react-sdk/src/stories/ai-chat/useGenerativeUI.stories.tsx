import type { Meta, StoryObj } from "@storybook/react-vite";
import { useCallback, useEffect, useRef, useState } from "react";
import { vinylDocuments } from "../__mocks__/fixtures";

/**
 * # useGenerativeUI
 *
 * Turns a **name → React component registry** into client-tool handlers, so the
 * agent can answer with *rendered UI* instead of a paragraph of prose. When the
 * model calls a tool whose name matches a registered component (e.g.
 * `album_card`), the hook renders that component with the tool arguments as
 * props and adds it to `items`. The turn is *parked* on a promise until the
 * component calls back through `respond(id, value)` — instantly for display-only
 * cards, or on user interaction for a picker/configurator — and the resolved
 * value flows back to the agent as the tool result.
 *
 * This is the "generative UI" pattern: the LLM decides *what* to show, your
 * registry decides *how* to render it. *"show me jazz under $30"* comes back as
 * rich product cards (album art, star rating, price, condition), not prose.
 *
 * ## Key Features
 * - `clientTools`: spread into `useTuringChat({ clientTools })` — one handler per
 *   registered component name (the agent declares the same names as client tools)
 * - `items`: pending generative items (`{ id, component, props }`) for `TuringGenerativeContent`
 * - `respond(id, value)`: resolve a rendered component's parked tool call (wire to `onRespond`)
 * - `clear()`: drop all pending items, resolving their parked calls with `undefined`
 * - **Registry-driven dispatch**: `item.component` → React component, so one chat
 *   surface renders cards, pickers, tables, charts — whatever you register
 *
 * ## Usage
 * ```tsx
 * // 1. A registry maps a component name to the React component that renders it.
 * const registry = { album_card: AlbumCard, condition_picker: ConditionPicker };
 *
 * // 2. The hook builds client-tool handlers from the registry keys.
 * const gen = useGenerativeUI(registry);
 *
 * // 3. Those handlers are passed to the chat so the agent can "call" them.
 * const chat = useTuringChat({ agent, clientTools: gen.clientTools });
 *
 * // 4. Render the parked items through the same registry.
 * <TuringGenerativeContent
 *   items={gen.items}
 *   registry={registry}
 *   onRespond={gen.respond}
 * />
 * ```
 *
 * The agent must declare the same tool names as client tools (`clientToolsJson`,
 * `clientToolsEnabled`); component names map 1:1 to client-tool names.
 *
 * ## When to use
 * - **Product / catalog answers**: a shopping agent should return *cards* the user
 *   can scan and click, not a markdown list — far higher conversion than prose
 * - **Interactive follow-ups**: the model needs a value back (pick a condition,
 *   choose a shipping option) — the parked promise turns a card into a form whose
 *   answer continues the turn
 * - **Mixed rich output**: interleave tables, charts, and cards in one reply by
 *   registering one component per output type
 *
 * ## About this story
 * Fully self-contained — no real hook, Provider, or network. It simulates the LLM
 * *streaming* a sequence of generative UI items: first a short text intro, then
 * one `album_card` item per matching record (built from the shared
 * `vinylDocuments` fixture, filtered to *jazz under $30*-style queries). Each card
 * progressively pops in as the stream "emits" it, illustrating how the registry
 * dispatches `item.type` → component. The fallback story shows what happens when
 * the model emits an unknown component type.
 */

/* ── Local demo types (self-contained — mirrors the hook's item shape) ── */

interface DemoGenerativeItem {
  id: string;
  /** Maps 1:1 to a registry key — the "tool name" the model called. */
  component: string;
  props: Record<string, unknown>;
}

interface AlbumCardProps {
  id: string;
  title: string;
  artist: string;
  genre: string;
  price: number;
  rating: number;
  condition: string;
  year: number;
  coverArt: string;
}

/** A registry maps a component name to the function that renders its props. */
type DemoRegistry = Record<
  string,
  (props: Record<string, unknown>, isDark: boolean) => React.ReactNode
>;

/* ── Fixture → demo album props (the "search result" the agent turns into UI) ── */

const ALBUMS: AlbumCardProps[] = vinylDocuments.map((doc) => {
  const f = doc.fields as Record<string, unknown>;
  const genres = Array.isArray(f.genre) ? (f.genre as string[]) : [String(f.genre)];
  return {
    id: String(f.id),
    title: String(f.title),
    artist: String(f.artist),
    genre: genres[0],
    price: Number(f.price),
    rating: Number(f.rating),
    condition: String(f.condition),
    year: Number(f.year),
    coverArt: String(f.cover_art ?? f.image),
  };
});

/* ── Card component, registered under "album_card" ── */

const CONDITION_COLORS: Record<string, string> = {
  "Near Mint": "#059669",
  "Very Good Plus": "#0d9488",
  "Very Good": "#2563eb",
  "Good Plus": "#d97706",
};

function StarRating({ rating }: { rating: number }) {
  return (
    <span style={{ letterSpacing: "1px", fontSize: "13px", color: "#f59e0b" }} aria-label={`${rating} of 5 stars`}>
      {"★".repeat(rating)}
      <span style={{ color: "#cbd5e1" }}>{"★".repeat(5 - rating)}</span>
    </span>
  );
}

function AlbumCard(props: Record<string, unknown>, isDark: boolean): React.ReactNode {
  const a = props as unknown as AlbumCardProps;
  const conditionColor = CONDITION_COLORS[a.condition] ?? "#475569";
  return (
    <div
      style={{
        display: "flex",
        gap: "14px",
        padding: "12px",
        borderRadius: "14px",
        border: `1px solid ${isDark ? "#1e293b" : "#e2e8f0"}`,
        background: isDark ? "#11182740" : "#ffffff",
        boxShadow: isDark ? "none" : "0 1px 3px rgba(15,23,42,0.06)",
        animation: "genPop 0.35s ease",
      }}
    >
      <img
        src={a.coverArt}
        alt={`${a.title} cover art`}
        width={92}
        height={92}
        style={{
          width: 92,
          height: 92,
          borderRadius: "10px",
          objectFit: "cover",
          flexShrink: 0,
          boxShadow: "0 2px 8px rgba(15,23,42,0.18)",
        }}
      />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: "8px" }}>
          <div style={{ fontWeight: 700, fontSize: "15px", color: isDark ? "#f1f5f9" : "#0f172a" }}>
            {a.title}
          </div>
          <div
            style={{
              fontWeight: 800,
              fontSize: "16px",
              background: "linear-gradient(90deg,#2563eb,#4f46e5)",
              WebkitBackgroundClip: "text",
              backgroundClip: "text",
              WebkitTextFillColor: "transparent",
              whiteSpace: "nowrap",
            }}
          >
            ${a.price.toFixed(2)}
          </div>
        </div>
        <div style={{ fontSize: "13px", color: isDark ? "#94a3b8" : "#64748b", marginTop: "1px" }}>
          {a.artist} · {a.year}
        </div>
        <div style={{ marginTop: "7px", display: "flex", alignItems: "center", gap: "8px" }}>
          <StarRating rating={a.rating} />
          <span
            style={{
              fontSize: "10px",
              fontWeight: 700,
              textTransform: "uppercase",
              letterSpacing: "0.04em",
              color: "#fff",
              background: conditionColor,
              padding: "2px 8px",
              borderRadius: "999px",
            }}
          >
            {a.condition}
          </span>
          <span
            style={{
              fontSize: "11px",
              color: isDark ? "#60a5fa" : "#4f46e5",
              border: `1px solid ${isDark ? "#1e3a8a" : "#c7d2fe"}`,
              padding: "1px 7px",
              borderRadius: "999px",
            }}
          >
            {a.genre}
          </span>
        </div>
      </div>
    </div>
  );
}

/** Fallback for an item whose `component` isn't in the registry. */
function UnknownComponent(props: Record<string, unknown>, isDark: boolean): React.ReactNode {
  return (
    <div
      style={{
        padding: "12px 14px",
        borderRadius: "10px",
        border: `1px dashed ${isDark ? "#7f1d1d" : "#fca5a5"}`,
        background: isDark ? "#450a0a30" : "#fef2f2",
        color: isDark ? "#fca5a5" : "#991b1b",
        fontSize: "12px",
        fontFamily: "ui-monospace, monospace",
      }}
    >
      ⚠️ No component registered for <strong>{String(props.__type ?? "unknown")}</strong> — falling back to JSON:
      <pre style={{ margin: "6px 0 0", whiteSpace: "pre-wrap" }}>{JSON.stringify(props, null, 2)}</pre>
    </div>
  );
}

const REGISTRY: DemoRegistry = {
  album_card: AlbumCard,
};

/* ── The simulated stream of generative items ── */

function pickMatches(query: string): AlbumCardProps[] {
  // Toy NL parse: "jazz under $30" → genre + max price.
  const lower = query.toLowerCase();
  const genreMatch = ["jazz", "rock", "pop", "art rock"].find((g) => lower.includes(g));
  const priceMatch = /under \$?(\d+)/.exec(lower);
  const maxPrice = priceMatch ? Number(priceMatch[1]) : Infinity;
  return ALBUMS.filter(
    (a) =>
      (!genreMatch || a.genre.toLowerCase().includes(genreMatch)) && a.price <= maxPrice,
  );
}

/* ── Demo component (simulates useGenerativeUI + TuringGenerativeContent) ── */

function GenerativeUIDemo({
  query,
  theme,
  forceUnknownType,
}: {
  query: string;
  theme: "light" | "dark";
  forceUnknownType: boolean;
}) {
  const isDark = theme === "dark";
  const [intro, setIntro] = useState("");
  const [items, setItems] = useState<DemoGenerativeItem[]>([]);
  const [streaming, setStreaming] = useState(false);
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);

  const clearTimers = useCallback(() => {
    timers.current.forEach(clearTimeout);
    timers.current = [];
  }, []);

  const run = useCallback(() => {
    clearTimers();
    setIntro("");
    setItems([]);
    setStreaming(true);

    const matches = forceUnknownType ? [] : pickMatches(query);
    const introText = forceUnknownType
      ? "Here's something I haven't taught your registry to render yet…"
      : matches.length > 0
        ? `Found ${matches.length} record${matches.length > 1 ? "s" : ""} matching "${query}":`
        : `No records match "${query}" — try a wider price range.`;

    // 1) Stream the intro text token-by-token.
    let pos = 0;
    const typeIntro = () => {
      pos += 2;
      setIntro(introText.slice(0, pos));
      if (pos < introText.length) {
        timers.current.push(setTimeout(typeIntro, 24));
      } else {
        // 2) Then emit one generative item per matching album.
        if (forceUnknownType) {
          timers.current.push(
            setTimeout(() => {
              setItems([
                {
                  id: "gen-unknown",
                  component: "playlist_carousel", // not in REGISTRY → fallback
                  props: { __type: "playlist_carousel", mood: "late-night", tracks: 12 },
                },
              ]);
              setStreaming(false);
            }, 250),
          );
          return;
        }
        matches.forEach((album, i) => {
          timers.current.push(
            setTimeout(
              () => {
                setItems((prev) => [
                  ...prev,
                  {
                    id: `gen-${album.id}`,
                    component: "album_card",
                    props: { ...album } as unknown as Record<string, unknown>,
                  },
                ]);
                if (i === matches.length - 1) setStreaming(false);
              },
              350 + i * 280,
            ),
          );
        });
        if (matches.length === 0) setStreaming(false);
      }
    };
    timers.current.push(setTimeout(typeIntro, 200));
  }, [clearTimers, forceUnknownType, query]);

  // Auto-run on mount and whenever the controls change.
  useEffect(() => {
    run();
    return clearTimers;
  }, [run, clearTimers]);

  return (
    <div style={{ fontFamily: "system-ui, sans-serif", width: "440px", maxWidth: "100%" }}>
      {/* Chat header */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: "10px",
          padding: "12px 16px",
          borderRadius: "14px 14px 0 0",
          background: "linear-gradient(90deg,#2563eb,#4f46e5)",
          color: "#fff",
        }}
      >
        <span style={{ fontSize: "20px" }}>🎵</span>
        <div>
          <div style={{ fontWeight: 700, fontSize: "14px" }}>Vinyl Concierge</div>
          <div style={{ fontSize: "11px", opacity: 0.85 }}>generative UI · cards, not paragraphs</div>
        </div>
        <button
          type="button"
          onClick={run}
          style={{
            marginLeft: "auto",
            border: "none",
            background: "rgba(255,255,255,0.18)",
            color: "#fff",
            fontSize: "12px",
            fontWeight: 600,
            padding: "5px 12px",
            borderRadius: "999px",
            cursor: "pointer",
          }}
        >
          ↻ Replay
        </button>
      </div>

      {/* Chat body */}
      <div
        style={{
          padding: "16px",
          background: isDark ? "#0b1120" : "#f8fafc",
          borderRadius: "0 0 14px 14px",
          border: `1px solid ${isDark ? "#1e293b" : "#e2e8f0"}`,
          borderTop: "none",
        }}
      >
        {/* User bubble */}
        <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: "12px" }}>
          <div
            style={{
              background: "linear-gradient(90deg,#2563eb,#4f46e5)",
              color: "#fff",
              padding: "8px 14px",
              borderRadius: "16px 16px 4px 16px",
              fontSize: "13px",
              maxWidth: "80%",
            }}
          >
            {query}
          </div>
        </div>

        {/* Assistant intro text */}
        <div
          style={{
            fontSize: "13px",
            color: isDark ? "#cbd5e1" : "#334155",
            marginBottom: items.length > 0 ? "12px" : 0,
            minHeight: "18px",
          }}
        >
          {intro}
          {streaming && intro.length > 0 && items.length === 0 && (
            <span
              style={{
                display: "inline-block",
                width: 6,
                height: 13,
                background: "#4f46e5",
                marginLeft: 2,
                verticalAlign: "middle",
                animation: "genBlink 1s steps(2) infinite",
              }}
            />
          )}
        </div>

        {/* Generative items — dispatched through the registry */}
        <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
          {items.map((item) => {
            const render = REGISTRY[item.component] ?? UnknownComponent;
            return <div key={item.id}>{render(item.props, isDark)}</div>;
          })}
        </div>

        {/* Tool-call trace — teaches the registry/dispatch concept */}
        {items.length > 0 && (
          <div
            style={{
              marginTop: "14px",
              fontSize: "11px",
              fontFamily: "ui-monospace, monospace",
              color: isDark ? "#64748b" : "#94a3b8",
              borderTop: `1px dashed ${isDark ? "#1e293b" : "#e2e8f0"}`,
              paddingTop: "10px",
            }}
          >
            <div style={{ marginBottom: "4px" }}>tool calls → registry dispatch:</div>
            {items.map((item) => (
              <div key={item.id}>
                {REGISTRY[item.component] ? "✓" : "✗"} {item.component}(
                {Object.keys(item.props).slice(0, 3).join(", ")}…)
              </div>
            ))}
          </div>
        )}
      </div>

      <style>{`
        @keyframes genBlink { 50% { opacity: 0; } }
        @keyframes genPop {
          from { opacity: 0; transform: translateY(6px) scale(0.98); }
          to { opacity: 1; transform: none; }
        }
      `}</style>
    </div>
  );
}

const meta: Meta<typeof GenerativeUIDemo> = {
  title: "AI & Chat/useGenerativeUI",
  component: GenerativeUIDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Self-contained simulation of `useGenerativeUI`. The agent answers a vinyl-store query by *streaming* generative UI items that a name→component registry renders as rich album cards (cover art, star rating, price, condition badge) instead of plain text. Use the controls to change the query, switch light/dark, or force an unknown component type to see the registry fallback. No hook, Provider, or network involved.",
      },
    },
  },
  argTypes: {
    query: {
      description: "The user prompt. A toy NL parser maps it to genre + max price (e.g. 'jazz under $30').",
      control: { type: "text" },
    },
    theme: {
      description: "Light or dark chat surface.",
      control: { type: "inline-radio" },
      options: ["light", "dark"],
    },
    forceUnknownType: {
      description: "Emit a component type not in the registry to preview the fallback renderer.",
      control: { type: "boolean" },
    },
  },
};

export default meta;
type Story = StoryObj<typeof GenerativeUIDemo>;

export const JazzUnderThirty: Story = {
  name: "🎵 'show me jazz under $30' (cards stream in)",
  args: { query: "show me jazz under $30", theme: "light", forceUnknownType: false },
};

export const RockPicksDark: Story = {
  name: "🃏 Rock picks (dark)",
  args: { query: "best rock records under $90", theme: "dark", forceUnknownType: false },
};

export const EverythingInStock: Story = {
  name: "🎵 Whole catalog as cards",
  args: { query: "show me everything you've got", theme: "light", forceUnknownType: false },
};

export const UnknownComponentFallback: Story = {
  name: "🃏 Unregistered component → fallback",
  args: { query: "make me a late-night playlist", theme: "dark", forceUnknownType: true },
};
