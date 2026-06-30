import type { Meta, StoryObj } from "@storybook/react-vite";
import { useMemo, useState } from "react";
import type { TurSpellCheck } from "../../core/types";
import { creatureDocuments, resolveDocsMock } from "../__mocks__/fixtures";

/**
 * # useTuringSpellCheck
 *
 * "Did you mean?" — surfaces a corrected-query suggestion when a search
 * term is misspelled. Wraps T402's `GET /sn/{site}/{locale}/spell-check`
 * so a search box can prompt the user to retry with the corrected text
 * **without parsing the full search bean**.
 *
 * The hook either **auto-checks** as the `query` option changes, or runs
 * imperatively via `check(query)`. It distills the raw `TurSpellCheck`
 * widget into a single convenient `suggestion` string — `null` when the
 * engine has nothing better to offer — so the UI is a one-liner:
 *
 * > _User types `dragn` → banner appears: "Did you mean **dragon**?" →
 * > click → query becomes `dragon` and the real results show up._
 *
 * ## Key Features
 * - `query` option: auto-checks on mount and on every query change
 * - `enabled` option: pause auto-checking (e.g. while the box is empty)
 * - `locale` option: per-call locale, falling back to the provider locale
 * - `suggestion`: the corrected text, or `null` when no correction —
 *   the only field most callers need
 * - `spellCheck`: the raw `TurSpellCheck` (original/corrected links +
 *   `usingCorrectedText`) for advanced UIs
 * - `check(query)`: imperative trigger (debounce it yourself)
 * - `loading` / `error`: in-flight + failure state for the banner
 *
 * ## Usage
 * ```tsx
 * function SearchBox() {
 *   const [query, setQuery] = useState("");
 *   const { suggestion, loading } = useTuringSpellCheck({
 *     query,
 *     locale: "en",
 *     enabled: query.length >= 2,
 *   });
 *
 *   return (
 *     <>
 *       <input value={query} onChange={(e) => setQuery(e.target.value)} />
 *       {!loading && suggestion && (
 *         <button onClick={() => setQuery(suggestion)}>
 *           Did you mean <strong>{suggestion}</strong>?
 *         </button>
 *       )}
 *     </>
 *   );
 * }
 * ```
 *
 * ## When to use
 *
 * - **Zero-result rescue**: a typo (`phenix`) returns nothing — offer the
 *   correction instead of a dead end
 * - **Low-result nudge**: even with a few hits, surfacing "Did you mean
 *   phoenix?" lifts click-through
 * - **Auto-correct mode**: read `usingCorrectedText` to silently run the
 *   corrected query and show a "Showing results for …" notice
 *
 * ## About this story
 *
 * Fully self-contained — no real hook, Provider, or network. A tiny local
 * misspelling→correction map (keyed off the Mythical Creatures bestiary)
 * stands in for the spell-check endpoint: as you type a wrong spelling
 * (`dragn`, `phenix`, `krakn`, `unecorn`…) the "Did you mean?" banner
 * appears; clicking it rewrites the query and shows the corrected
 * creature results resolved from the shared `creatureDocuments` fixture.
 */

/* ── Local simulation: misspelling → canonical creature name ──
   Keyed off the bestiary in the shared fixture. In production the
   backend's analyzer/suggester returns this; here we fake it. */
const CORRECTIONS: Readonly<Record<string, string>> = {
  dragn: "dragon",
  dragohn: "dragon",
  drago: "dragon",
  phenix: "phoenix",
  phenex: "phoenix",
  feenix: "phoenix",
  krakn: "kraken",
  krackin: "kraken",
  unecorn: "unicorn",
  unikorn: "unicorn",
  thunderbrd: "thunderbird",
  cerberos: "cerberus",
};

/** The canonical creature titles, lower-cased, for the "already correct" check. */
const KNOWN_TERMS: ReadonlyArray<string> =
  creatureDocuments.map((d) => String(d.fields.title).toLowerCase());

/**
 * Local stand-in for `fetchSpellCheck` — returns the same `TurSpellCheck`
 * shape the real endpoint does. `correctedText` flips true only when we
 * have a better spelling for the typed term.
 */
function simulateSpellCheck(raw: string): TurSpellCheck {
  const q = raw.trim().toLowerCase();
  const fix = CORRECTIONS[q];
  const linkFor = (text: string) => `?q=${encodeURIComponent(text)}`;
  if (fix) {
    return {
      correctedText: true,
      usingCorrectedText: false,
      original: { link: linkFor(raw), text: raw },
      corrected: { link: linkFor(fix), text: fix },
    };
  }
  return {
    correctedText: false,
    usingCorrectedText: false,
    original: { link: linkFor(raw), text: raw },
    corrected: { link: "", text: "" },
  };
}

interface Theme {
  readonly bg: string;
  readonly card: string;
  readonly border: string;
  readonly text: string;
  readonly muted: string;
  readonly inputBg: string;
}

const LIGHT: Theme = {
  bg: "transparent",
  card: "#ffffff",
  border: "#e2e8f0",
  text: "#0f172a",
  muted: "#64748b",
  inputBg: "#ffffff",
};

const DARK: Theme = {
  bg: "#0a0a0f",
  card: "#16161f",
  border: "#1e1e2e",
  text: "#e2e8f0",
  muted: "#94a3b8",
  inputBg: "#0f0f17",
};

const GRADIENT = "linear-gradient(135deg, #2563eb 0%, #4f46e5 100%)";

function SpellCheckDemo({
  initialQuery,
  dark,
}: {
  initialQuery: string;
  dark: boolean;
}) {
  const theme = dark ? DARK : LIGHT;
  const [query, setQuery] = useState(initialQuery);

  // What the "hook" would expose for the current query.
  const spellCheck = useMemo<TurSpellCheck | null>(
    () => (query.trim().length >= 2 ? simulateSpellCheck(query) : null),
    [query],
  );
  const suggestion =
    spellCheck?.correctedText && spellCheck.corrected.text
      ? spellCheck.corrected.text
      : null;

  // The effective term we search with: the suggestion is *offered*, not
  // auto-applied — results reflect whatever the user has actually typed.
  const effective = query.trim().toLowerCase();
  const isKnown = KNOWN_TERMS.includes(effective);

  const results = useMemo(() => {
    if (!isKnown) return [];
    return resolveDocsMock(
      creatureDocuments.filter(
        (d) => String(d.fields.title).toLowerCase() === effective,
      ),
    );
  }, [effective, isKnown]);

  const accept = () => {
    if (suggestion) setQuery(suggestion);
  };

  return (
    <div
      style={{
        fontFamily: "system-ui, sans-serif",
        width: "440px",
        background: theme.bg,
        padding: dark ? "20px" : "0",
        borderRadius: "12px",
      }}
    >
      <p style={{ fontSize: "12px", color: theme.muted, marginBottom: "10px" }}>
        🔤 Try a typo: <code>dragn</code>, <code>phenix</code>,{" "}
        <code>krakn</code>, <code>unecorn</code>, <code>cerberos</code>
      </p>

      {/* Search input */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: "8px",
          border: `2px solid ${theme.border}`,
          borderRadius: "10px",
          padding: "10px 14px",
          background: theme.inputBg,
        }}
      >
        <span style={{ fontSize: "16px" }}>🔍</span>
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search the bestiary…"
          style={{
            flex: 1,
            border: "none",
            outline: "none",
            background: "transparent",
            fontSize: "15px",
            color: theme.text,
          }}
        />
        {query && (
          <button
            type="button"
            onClick={() => setQuery("")}
            style={{
              background: "none",
              border: "none",
              cursor: "pointer",
              color: theme.muted,
              fontSize: "16px",
            }}
          >
            ✕
          </button>
        )}
      </div>

      {/* "Did you mean?" banner — the whole point of the hook */}
      {suggestion && (
        <button
          type="button"
          onClick={accept}
          style={{
            marginTop: "10px",
            width: "100%",
            display: "flex",
            alignItems: "center",
            gap: "8px",
            padding: "10px 14px",
            border: "none",
            borderRadius: "10px",
            background: GRADIENT,
            color: "white",
            fontSize: "14px",
            cursor: "pointer",
            textAlign: "left",
          }}
        >
          <span style={{ fontSize: "16px" }}>🐉</span>
          <span>
            Did you mean <strong>{suggestion}</strong>?{" "}
            <span style={{ opacity: 0.85 }}>— click to correct</span>
          </span>
        </button>
      )}

      {/* Results / empty states */}
      <div style={{ marginTop: "16px" }}>
        {results.length > 0 ? (
          results.map((doc) => (
            <div
              key={doc.url}
              style={{
                display: "flex",
                gap: "12px",
                padding: "12px",
                border: `1px solid ${theme.border}`,
                borderRadius: "10px",
                background: theme.card,
                marginBottom: "10px",
              }}
            >
              <img
                src={doc.image}
                alt={doc.title}
                width={72}
                height={54}
                style={{
                  borderRadius: "6px",
                  objectFit: "cover",
                  flexShrink: 0,
                }}
              />
              <div>
                <div
                  style={{
                    fontWeight: 600,
                    fontSize: "15px",
                    color: theme.text,
                    marginBottom: "2px",
                  }}
                >
                  {doc.title}
                </div>
                <div
                  style={{
                    fontSize: "12px",
                    color: theme.muted,
                    lineHeight: 1.4,
                  }}
                >
                  {doc.description}
                </div>
              </div>
            </div>
          ))
        ) : (
          <div
            style={{
              padding: "16px",
              border: `1px dashed ${theme.border}`,
              borderRadius: "10px",
              fontSize: "13px",
              color: theme.muted,
              textAlign: "center",
            }}
          >
            {query.trim().length < 2
              ? "Start typing to search the bestiary…"
              : suggestion
                ? `No results for “${query}”. Accept the suggestion above.`
                : `No results for “${query}”.`}
          </div>
        )}
      </div>

      {/* Hook state inspector */}
      <div
        style={{
          marginTop: "16px",
          padding: "10px 12px",
          background: dark ? "#0f0f17" : "#f8fafc",
          border: `1px solid ${theme.border}`,
          borderRadius: "8px",
          fontSize: "11px",
          color: theme.muted,
          lineHeight: 1.7,
        }}
      >
        <div>
          <strong>suggestion:</strong>{" "}
          <code>{suggestion ? `"${suggestion}"` : "null"}</code>
        </div>
        <div>
          <strong>spellCheck.correctedText:</strong>{" "}
          <code>{String(spellCheck?.correctedText ?? false)}</code>
        </div>
        <div>
          <strong>results:</strong> <code>{results.length}</code>
        </div>
      </div>
    </div>
  );
}

const meta: Meta<typeof SpellCheckDemo> = {
  title: "Discovery/useTuringSpellCheck",
  component: SpellCheckDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          'Interactive "Did you mean?" demo simulating `useTuringSpellCheck`. A local misspelling→correction map keyed off the Mythical Creatures bestiary stands in for the T402 spell-check endpoint — type a typo (e.g. `dragn`, `phenix`, `krakn`) to see the gradient correction banner, then click it to rewrite the query and reveal the corrected creature results. Fully self-contained: no real hook, Provider, or network.',
      },
    },
  },
  argTypes: {
    initialQuery: {
      description: "Pre-filled (mis)spelling shown when the story loads.",
      control: { type: "text" },
    },
    dark: {
      description: "Render against the Viglet dark surface (#0a0a0f).",
      control: { type: "boolean" },
    },
  },
};

export default meta;
type Story = StoryObj<typeof SpellCheckDemo>;

export const MisspelledDragon: Story = {
  name: "🐉 Misspelled “dragn” → dragon",
  args: { initialQuery: "dragn", dark: false },
};

export const MisspelledPhoenix: Story = {
  name: "🔤 “phenix” → phoenix",
  args: { initialQuery: "phenix", dark: false },
};

export const CorrectlySpelled: Story = {
  name: "✅ Correct spelling (no banner)",
  args: { initialQuery: "kraken", dark: false },
};

export const DarkMode: Story = {
  name: "🌙 Dark — “krakn” → kraken",
  args: { initialQuery: "krakn", dark: true },
};
