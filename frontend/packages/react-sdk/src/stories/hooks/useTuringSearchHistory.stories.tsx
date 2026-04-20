import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";

/**
 * # useTuringSearchHistory
 *
 * Persists search terms in the browser's IndexedDB, scoped by site name
 * and origin domain. Supports save, remove, and clear.
 *
 * ## Key Features
 * - `history`: Array of recent search terms (newest first)
 * - `save(term)`: Save a search term (deduplicates)
 * - `remove(term)`: Delete a specific term
 * - `clear()`: Delete all history for the current site
 * - Scoped per site + domain (no cross-site leakage)
 * - Works with IndexedDB for persistence across sessions
 *
 * ## Usage
 * ```tsx
 * const { history, save, remove, clear } = useTuringSearchHistory();
 *
 * function onSearch(term: string) {
 *   save(term);
 *   turing.submitSearch();
 * }
 *
 * history.map(term => <div key={term}>{term}</div>)
 * ```
 *
 * This story uses in-memory state to simulate the behavior.
 */

function SearchHistoryDemo({ maxItems }: { maxItems: number }) {
  const [history, setHistory] = useState<string[]>([
    "dragon fire",
    "phoenix mythology",
    "kraken deep sea",
    "apollo 11 moon landing",
    "vinyl jazz records",
  ]);
  const [input, setInput] = useState("");

  function save(term: string) {
    const clean = term.trim();
    if (!clean || clean === "*") return;
    setHistory((prev) => {
      const filtered = prev.filter((h) => h !== clean);
      return [clean, ...filtered].slice(0, maxItems);
    });
  }

  function remove(term: string) {
    setHistory((prev) => prev.filter((h) => h !== term));
  }

  function clearAll() {
    setHistory([]);
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (input.trim()) {
      save(input.trim());
      setInput("");
    }
  }

  return (
    <div style={{ fontFamily: "system-ui, sans-serif", maxWidth: "400px" }}>
      {/* Search input */}
      <form onSubmit={handleSubmit} style={{ display: "flex", gap: "8px", marginBottom: "16px" }}>
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Type a search and press Enter..."
          style={{
            flex: 1,
            padding: "10px 14px",
            border: "2px solid #e2e8f0",
            borderRadius: "10px",
            fontSize: "14px",
            outline: "none",
          }}
        />
        <button
          type="submit"
          style={{
            padding: "10px 16px",
            borderRadius: "10px",
            border: "none",
            background: "linear-gradient(135deg, #2563eb, #4f46e5)",
            color: "white",
            fontWeight: 600,
            cursor: "pointer",
            fontSize: "14px",
          }}
        >
          Save
        </button>
      </form>

      {/* History list */}
      <div
        style={{
          border: "1px solid #e2e8f0",
          borderRadius: "12px",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            padding: "10px 14px",
            borderBottom: "1px solid #e2e8f0",
            background: "#f8fafc",
          }}
        >
          <span style={{ fontSize: "12px", fontWeight: 600, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.05em" }}>
            Recent Searches ({history.length})
          </span>
          {history.length > 0 && (
            <button
              onClick={clearAll}
              style={{
                fontSize: "12px",
                color: "#ef4444",
                background: "none",
                border: "none",
                cursor: "pointer",
              }}
            >
              Clear all
            </button>
          )}
        </div>

        {history.length === 0 ? (
          <div style={{ padding: "24px", textAlign: "center", color: "#94a3b8", fontSize: "13px" }}>
            No recent searches
          </div>
        ) : (
          history.map((term) => (
            <div
              key={term}
              style={{
                display: "flex",
                alignItems: "center",
                borderBottom: "1px solid #f1f5f9",
              }}
            >
              <button
                onClick={() => setInput(term)}
                style={{
                  flex: 1,
                  display: "flex",
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
              >
                <span style={{ color: "#94a3b8", fontSize: "12px" }}>🕐</span>
                {term}
              </button>
              <button
                onClick={() => remove(term)}
                style={{
                  padding: "10px 14px",
                  border: "none",
                  background: "transparent",
                  cursor: "pointer",
                  color: "#94a3b8",
                  fontSize: "14px",
                }}
                title="Remove"
              >
                ✕
              </button>
            </div>
          ))
        )}
      </div>

      {/* Info box */}
      <div
        style={{
          marginTop: "16px",
          padding: "10px 14px",
          background: "#eff6ff",
          borderRadius: "8px",
          fontSize: "12px",
          color: "#3b82f6",
          lineHeight: 1.5,
        }}
      >
        💡 In the real SDK, history is persisted in <strong>IndexedDB</strong>, scoped by site name
        and domain. This demo uses in-memory state for illustration.
      </div>
    </div>
  );
}

const meta: Meta<typeof SearchHistoryDemo> = {
  title: "Hooks/useTuringSearchHistory",
  component: SearchHistoryDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Interactive search history demo. Type and save searches — click terms to reuse them, ✕ to remove. This simulates `useTuringSearchHistory`.",
      },
    },
  },
  argTypes: {
    maxItems: {
      description: "Maximum number of history entries to keep",
      control: { type: "range", min: 3, max: 20, step: 1 },
    },
  },
};

export default meta;
type Story = StoryObj<typeof SearchHistoryDemo>;

export const Default: Story = {
  args: {
    maxItems: 10,
  },
};

export const SmallHistory: Story = {
  name: "Small (Max 3)",
  args: {
    maxItems: 3,
  },
};
