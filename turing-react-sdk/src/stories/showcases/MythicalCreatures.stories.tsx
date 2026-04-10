import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import {
  resolveDocsMock,
  creatureDocuments,
  creatureFacets,
} from "../__mocks__/fixtures";
import type { ResolvedDocument } from "../../core/types";

/**
 * # 🐉 Mythical Creatures Showcase
 *
 * This showcase demonstrates a full search page inspired by the
 * `turing-marketplace/mythical-creatures` project — a fantasy bestiary
 * with element badges, danger-level bars, and faceted filtering.
 *
 * ## SDK Components Used
 * - `TuringProvider` — wraps the page with site config
 * - `useTuringFacets` — facet sidebar with toggle/clear
 * - `useTuringPagination` — page navigation
 * - `useTuringDocument` — typed field access (element, danger_level, creature_type)
 * - `TuringSearchField` — search with autocomplete and history
 * - `TuringResultList` — renders creature cards
 *
 * ## Theme Details
 * - Dark purple/indigo palette
 * - Element-based color badges (Fire → orange, Water → cyan, Shadow → violet)
 * - Danger level progress bar (green → amber → red)
 * - Creature type tags
 */

const elementColors: Record<string, { bg: string; text: string; border: string }> = {
  Fire: { bg: "#ff6b3520", text: "#ff6b35", border: "#ff6b3540" },
  Water: { bg: "#06b6d420", text: "#06b6d4", border: "#06b6d440" },
  Earth: { bg: "#10b98120", text: "#10b981", border: "#10b98140" },
  Air: { bg: "#38bdf820", text: "#38bdf8", border: "#38bdf840" },
  Lightning: { bg: "#eab30820", text: "#eab308", border: "#eab30840" },
  Shadow: { bg: "#8b5cf620", text: "#8b5cf6", border: "#8b5cf640" },
  Spirit: { bg: "#6366f120", text: "#6366f1", border: "#6366f140" },
  Ice: { bg: "#93c5fd20", text: "#93c5fd", border: "#93c5fd40" },
};

function DangerBar({ level }: { level: number }) {
  const pct = Math.min(level * 10, 100);
  const color = level >= 8 ? "#ef4444" : level >= 5 ? "#f59e0b" : "#10b981";
  return (
    <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
      <span style={{ fontSize: "12px" }}>☠️</span>
      <div
        style={{
          flex: 1,
          height: "6px",
          borderRadius: "3px",
          background: "#1e293b",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            width: `${pct}%`,
            height: "100%",
            borderRadius: "3px",
            background: `linear-gradient(90deg, ${color}80, ${color})`,
            transition: "width 0.5s ease",
          }}
        />
      </div>
      <span style={{ fontSize: "11px", fontFamily: "monospace", color: "#94a3b8", width: "20px", textAlign: "right" }}>
        {level}
      </span>
    </div>
  );
}

function CreatureCard({ doc }: { doc: ResolvedDocument }) {
  const fields = doc.raw.fields;
  const dangerLevel = (fields.danger_level as number) ?? 0;
  const elements = Array.isArray(fields.element)
    ? (fields.element as string[])
    : fields.element
      ? [fields.element as string]
      : [];
  const types = Array.isArray(fields.creature_type)
    ? (fields.creature_type as string[])
    : fields.creature_type
      ? [fields.creature_type as string]
      : [];

  return (
    <div
      style={{
        borderRadius: "12px",
        border: "1px solid #2d2d4e",
        background: "#1a1a2e",
        overflow: "hidden",
        transition: "all 0.3s ease",
        cursor: "pointer",
      }}
    >
      {doc.image && (
        <div style={{ position: "relative", height: "180px", overflow: "hidden" }}>
          <img
            src={doc.image}
            alt={doc.title}
            style={{ width: "100%", height: "100%", objectFit: "cover" }}
          />
          {dangerLevel > 0 && (
            <div
              style={{
                position: "absolute",
                top: "8px",
                right: "8px",
                padding: "2px 8px",
                borderRadius: "12px",
                background: "rgba(0,0,0,0.7)",
                backdropFilter: "blur(4px)",
                fontSize: "10px",
                fontFamily: "monospace",
                color: "#f59e0b",
                border: "1px solid rgba(245,158,11,0.3)",
              }}
            >
              🔥 {dangerLevel}
            </div>
          )}
        </div>
      )}
      <div style={{ padding: "14px" }}>
        <h3 style={{ margin: "0 0 6px", fontSize: "16px", fontWeight: 600, color: "#e2e8f0" }}>
          {doc.title}
        </h3>
        <p style={{ margin: "0 0 10px", fontSize: "12px", color: "#94a3b8", lineHeight: 1.5 }}>
          {doc.description}
        </p>

        {/* Tags */}
        <div style={{ display: "flex", flexWrap: "wrap", gap: "4px", marginBottom: "10px" }}>
          {types.map((t) => (
            <span
              key={t}
              style={{
                padding: "2px 8px",
                borderRadius: "10px",
                fontSize: "10px",
                background: "#334155",
                color: "#cbd5e1",
              }}
            >
              🪶 {t}
            </span>
          ))}
          {elements.map((el) => {
            const c = elementColors[el] ?? { bg: "#8b5cf620", text: "#8b5cf6", border: "#8b5cf640" };
            return (
              <span
                key={el}
                style={{
                  padding: "2px 8px",
                  borderRadius: "10px",
                  fontSize: "10px",
                  background: c.bg,
                  color: c.text,
                  border: `1px solid ${c.border}`,
                }}
              >
                {el}
              </span>
            );
          })}
        </div>

        {/* Danger bar */}
        {dangerLevel > 0 && <DangerBar level={dangerLevel} />}
      </div>
    </div>
  );
}

function BestiaryPage() {
  const allDocs = resolveDocsMock(creatureDocuments);
  const [query, setQuery] = useState("");
  const [selectedFacets, setSelectedFacets] = useState<Set<string>>(new Set());

  const filteredDocs = allDocs.filter((doc) => {
    if (query && !doc.title.toLowerCase().includes(query.toLowerCase()) &&
        !doc.description.toLowerCase().includes(query.toLowerCase())) {
      return false;
    }
    if (selectedFacets.size === 0) return true;
    for (const key of selectedFacets) {
      const [groupName, value] = key.split(":");
      const field = doc.raw.fields[groupName];
      if (Array.isArray(field) && field.includes(value)) return true;
      if (field === value) return true;
    }
    return false;
  });

  function toggleFacet(group: string, value: string) {
    const key = `${group}:${value}`;
    setSelectedFacets((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  }

  return (
    <div
      style={{
        minHeight: "100vh",
        background: "linear-gradient(180deg, #0f0f23, #1a1a2e)",
        color: "#e2e8f0",
        fontFamily: "system-ui, sans-serif",
      }}
    >
      {/* Header */}
      <header
        style={{
          padding: "16px 24px",
          borderBottom: "1px solid #2d2d4e",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <span style={{ fontSize: "20px" }}>✨</span>
          <span style={{ fontWeight: 700, fontSize: "14px", letterSpacing: "0.05em" }}>
            The Bestiary
          </span>
        </div>
        <span style={{ fontSize: "11px", color: "#64748b" }}>Powered by Viglet Turing ES</span>
      </header>

      {/* Hero / Search */}
      <section style={{ textAlign: "center", padding: "40px 24px 24px" }}>
        <div style={{ fontSize: "11px", color: "#8b5cf6", textTransform: "uppercase", letterSpacing: "0.2em", marginBottom: "8px" }}>
          Ancient Tome of
        </div>
        <h1 style={{ margin: "0 0 8px", fontSize: "36px", fontWeight: 800, color: "#f1f5f9" }}>
          The Bestiary
        </h1>
        <p style={{ color: "#94a3b8", maxWidth: "480px", margin: "0 auto 24px", fontSize: "14px" }}>
          Explore mythical creatures from world folklore. Dragons, spirits, leviathans and forgotten beasts await.
        </p>

        {/* Search bar */}
        <div style={{ maxWidth: "480px", margin: "0 auto" }}>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "8px",
              padding: "10px 16px",
              borderRadius: "12px",
              border: "1px solid #2d2d4e",
              background: "#1e1e38",
            }}
          >
            <span>🔍</span>
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search for a creature... dragon, phoenix, kraken..."
              style={{
                flex: 1,
                background: "transparent",
                border: "none",
                outline: "none",
                fontSize: "15px",
                color: "#e2e8f0",
              }}
            />
            <button
              onClick={() => setQuery("")}
              style={{
                padding: "6px 14px",
                borderRadius: "8px",
                border: "none",
                background: "linear-gradient(135deg, #6366f1, #8b5cf6)",
                color: "white",
                fontWeight: 600,
                fontSize: "13px",
                cursor: "pointer",
              }}
            >
              Search
            </button>
          </div>
        </div>
      </section>

      {/* Content */}
      <main style={{ display: "flex", gap: "24px", maxWidth: "1000px", margin: "0 auto", padding: "0 24px 48px" }}>
        {/* Sidebar */}
        <aside style={{ width: "200px", flexShrink: 0 }}>
          <div style={{ fontSize: "11px", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "#94a3b8", marginBottom: "12px" }}>
            🔍 Filters
          </div>
          {creatureFacets.map((group) => (
            <div key={group.name} style={{ marginBottom: "16px" }}>
              <h4 style={{ fontSize: "10px", fontWeight: 700, textTransform: "uppercase", color: "#64748b", marginBottom: "6px" }}>
                {group.label.text}
              </h4>
              {group.facets.map((facet) => {
                const isSelected = selectedFacets.has(`${group.name}:${facet.label}`);
                return (
                  <button
                    key={facet.label}
                    onClick={() => toggleFacet(group.name, facet.label)}
                    style={{
                      display: "flex",
                      width: "100%",
                      justifyContent: "space-between",
                      padding: "4px 8px",
                      borderRadius: "6px",
                      border: "none",
                      background: isSelected ? "rgba(99,102,241,0.15)" : "transparent",
                      color: isSelected ? "#818cf8" : "#94a3b8",
                      fontSize: "12px",
                      cursor: "pointer",
                      marginBottom: "2px",
                      textAlign: "left",
                    }}
                  >
                    <span>{isSelected ? "✕ " : ""}{facet.label}</span>
                    <span style={{ fontSize: "10px" }}>{facet.count}</span>
                  </button>
                );
              })}
            </div>
          ))}
        </aside>

        {/* Results grid */}
        <div style={{ flex: 1 }}>
          <p style={{ fontSize: "13px", color: "#64748b", marginBottom: "12px" }}>
            <strong style={{ color: "#e2e8f0" }}>{filteredDocs.length}</strong> creatures found
          </p>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))",
              gap: "16px",
            }}
          >
            {filteredDocs.map((doc) => (
              <CreatureCard key={doc.url} doc={doc} />
            ))}
          </div>
          {filteredDocs.length === 0 && (
            <div style={{ textAlign: "center", padding: "48px 0", color: "#475569" }}>
              😶 No creatures match your search. The bestiary pages are blank.
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

const meta: Meta<typeof BestiaryPage> = {
  title: "Showcases/🐉 Mythical Creatures",
  component: BestiaryPage,
  tags: ["autodocs"],
  parameters: {
    layout: "fullscreen",
    docs: {
      description: {
        component:
          "Full search page demo inspired by `turing-marketplace/mythical-creatures`. Features a dark bestiary theme with element badges, danger bars, facets, and search.",
      },
    },
  },
};

export default meta;
type Story = StoryObj<typeof BestiaryPage>;

export const Bestiary: Story = {
  name: "The Bestiary",
};
