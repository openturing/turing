import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState, useCallback } from "react";

/**
 * # useTuringAutoComplete
 *
 * Autocomplete hook with built-in debouncing. Calls the Turing `/ac` endpoint
 * and returns suggestion strings.
 *
 * ## Key Features
 * - Automatic debouncing (configurable, default 300ms)
 * - `suggestions`: Array of completion strings
 * - `fetch(query)`: Trigger a new autocomplete request
 * - `clear()`: Clear suggestions (e.g. on blur)
 * - Minimum 2 characters before fetching
 *
 * ## Usage
 * ```tsx
 * const { suggestions, fetch, clear } = useTuringAutoComplete(300);
 *
 * <input
 *   onChange={(e) => fetch(e.target.value)}
 *   onBlur={() => clear()}
 * />
 * {suggestions.map(s => <div key={s}>{s}</div>)}
 * ```
 *
 * This story simulates autocomplete with a local dictionary.
 */

/* ── Simulated suggestions data (creatures + missions + vinyl) ── */
const ALL_TERMS = [
  "dragon", "dragon fire", "dragon ice",
  "phoenix", "phoenix rising", "phoenix ash",
  "kraken", "kraken deep sea",
  "unicorn", "unicorn magic",
  "thunderbird", "thunderbird lightning",
  "cerberus", "cerberus gates",
  "apollo", "apollo 11", "apollo 13",
  "voyager", "voyager 1", "voyager 2",
  "mars", "mars perseverance", "mars rover",
  "rosetta", "rosetta comet",
  "challenger",
  "abbey road", "abbey road beatles",
  "kind of blue", "kind of blue miles davis",
  "rumours", "rumours fleetwood mac",
  "ok computer", "ok computer radiohead",
  "vinyl jazz", "vinyl rock", "vinyl pop",
];

/**
 * Safe text highlighter — splits text by the query match
 * and wraps matches in <strong> without using dangerouslySetInnerHTML.
 */
function HighlightedText({ text, highlight }: { text: string; highlight: string }) {
  if (!highlight || highlight.length < 2) return <span>{text}</span>;
  const escaped = highlight.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const parts = text.split(new RegExp(`(${escaped})`, "gi"));
  return (
    <span>
      {parts.map((part, i) =>
        part.toLowerCase() === highlight.toLowerCase() ? (
          <strong key={i}>{part}</strong>
        ) : (
          <span key={i}>{part}</span>
        ),
      )}
    </span>
  );
}

function AutoCompleteDemo({
  debounceMs,
  maxSuggestions,
}: {
  debounceMs: number;
  maxSuggestions: number;
}) {
  const [query, setQuery] = useState("");
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [isOpen, setIsOpen] = useState(false);

  const handleChange = useCallback(
    (value: string) => {
      setQuery(value);
      if (value.length < 2) {
        setSuggestions([]);
        return;
      }
      // Simulate debounced fetch
      const matches = ALL_TERMS
        .filter((t) => t.toLowerCase().includes(value.toLowerCase()))
        .slice(0, maxSuggestions);
      setSuggestions(matches);
      setIsOpen(matches.length > 0);
    },
    [maxSuggestions],
  );

  const handleSelect = (term: string) => {
    setQuery(term);
    setSuggestions([]);
    setIsOpen(false);
  };

  return (
    <div style={{ fontFamily: "system-ui, sans-serif", width: "400px" }}>
      <p style={{ fontSize: "12px", color: "#94a3b8", marginBottom: "8px" }}>
        Debounce: {debounceMs}ms · Max suggestions: {maxSuggestions} · Try typing &quot;dragon&quot;, &quot;apollo&quot;, or &quot;vinyl&quot;
      </p>

      <div style={{ position: "relative" }}>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            border: "2px solid #e2e8f0",
            borderRadius: isOpen ? "10px 10px 0 0" : "10px",
            padding: "10px 14px",
            gap: "8px",
            background: "white",
          }}
        >
          <span style={{ fontSize: "16px" }}>🔍</span>
          <input
            type="text"
            value={query}
            onChange={(e) => handleChange(e.target.value)}
            onFocus={() => suggestions.length > 0 && setIsOpen(true)}
            onBlur={() => setTimeout(() => setIsOpen(false), 150)}
            placeholder="Type at least 2 characters..."
            style={{
              flex: 1,
              border: "none",
              outline: "none",
              fontSize: "15px",
            }}
          />
          {query && (
            <button
              onClick={() => { setQuery(""); setSuggestions([]); setIsOpen(false); }}
              style={{
                background: "none",
                border: "none",
                cursor: "pointer",
                color: "#94a3b8",
                fontSize: "16px",
              }}
            >
              ✕
            </button>
          )}
        </div>

        {/* Dropdown */}
        {isOpen && suggestions.length > 0 && (
          <div
            style={{
              position: "absolute",
              top: "100%",
              left: 0,
              right: 0,
              border: "2px solid #e2e8f0",
              borderTop: "1px solid #f1f5f9",
              borderRadius: "0 0 10px 10px",
              background: "white",
              zIndex: 50,
              maxHeight: "240px",
              overflow: "auto",
            }}
          >
            {suggestions.map((term) => (
              <button
                key={term}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => handleSelect(term)}
                style={{
                  display: "flex",
                  width: "100%",
                  alignItems: "center",
                  gap: "8px",
                  padding: "10px 14px",
                  border: "none",
                  background: "transparent",
                  cursor: "pointer",
                  fontSize: "14px",
                  textAlign: "left",
                  color: "#334155",
                }}
                onMouseOver={(e) => (e.currentTarget.style.background = "#f8fafc")}
                onMouseOut={(e) => (e.currentTarget.style.background = "transparent")}
              >
                <span style={{ color: "#94a3b8", fontSize: "12px" }}>🔍</span>
                <HighlightedText text={term} highlight={query} />
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Debug info */}
      <div style={{ marginTop: "16px", fontSize: "12px", color: "#94a3b8" }}>
        <div>Query: <code style={{ color: "#6d28d9" }}>&quot;{query}&quot;</code></div>
        <div>Suggestions: <code>{suggestions.length}</code></div>
        <div>Open: <code>{String(isOpen)}</code></div>
      </div>
    </div>
  );
}

const meta: Meta<typeof AutoCompleteDemo> = {
  title: "Hooks/useTuringAutoComplete",
  component: AutoCompleteDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Interactive autocomplete demo simulating `useTuringAutoComplete`. Type to see suggestions from a local dictionary.",
      },
    },
  },
  argTypes: {
    debounceMs: {
      description: "Debounce delay in milliseconds",
      control: { type: "range", min: 0, max: 1000, step: 50 },
    },
    maxSuggestions: {
      description: "Maximum number of suggestions to show",
      control: { type: "range", min: 1, max: 15, step: 1 },
    },
  },
};

export default meta;
type Story = StoryObj<typeof AutoCompleteDemo>;

export const Default: Story = {
  args: {
    debounceMs: 300,
    maxSuggestions: 8,
  },
};

export const FastDebounce: Story = {
  name: "Fast Debounce (100ms)",
  args: {
    debounceMs: 100,
    maxSuggestions: 5,
  },
};

export const FewSuggestions: Story = {
  name: "Max 3 Suggestions",
  args: {
    debounceMs: 300,
    maxSuggestions: 3,
  },
};
