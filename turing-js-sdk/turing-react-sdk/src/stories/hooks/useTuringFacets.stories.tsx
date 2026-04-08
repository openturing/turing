import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import {
  resolveDocsMock,
  creatureDocuments,
  creatureFacets,
  missionDocuments,
  missionFacets,
  vinylDocuments,
  vinylFacets,
} from "../__mocks__/fixtures";
import type { TurFacetGroup, ResolvedDocument } from "../../core/types";

/**
 * # useTuringFacets
 *
 * Provides facet data with toggle/clear actions attached to each item.
 * In a real app, this reads from the shared search store via `TuringProvider`.
 *
 * ## Key Features
 * - `facetGroups`: Array of enriched groups with `toggle()` on each facet item
 * - `hasSelected`: Quick check if any facet is active
 * - `clearAll()`: Remove all selected facets at once
 * - Each group has `clear()` to remove selections within that group
 *
 * ## Usage
 * ```tsx
 * const { facetGroups, hasSelected, clearAll } = useTuringFacets();
 *
 * facetGroups.map(group => (
 *   <div key={group.name}>
 *     <h3>{group.label}</h3>
 *     {group.facets.map(facet => (
 *       <button onClick={facet.toggle}>
 *         {facet.selected ? "✕ " : ""}{facet.label} ({facet.count})
 *       </button>
 *     ))}
 *   </div>
 * ))
 * ```
 *
 * Since hooks require TuringProvider, this story simulates the facet behavior
 * with a standalone interactive component.
 */

/* ── Interactive FacetSidebar (simulates useTuringFacets) ── */

function FacetSidebar({
  facetGroups: initialGroups,
  documents: allDocs,
  theme,
}: {
  facetGroups: TurFacetGroup[];
  documents: ResolvedDocument[];
  theme: "light" | "dark";
}) {
  const [selectedFacets, setSelectedFacets] = useState<Set<string>>(new Set());
  const isDark = theme === "dark";

  function toggle(groupName: string, facetLabel: string) {
    const key = `${groupName}:${facetLabel}`;
    setSelectedFacets((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  function clearAll() {
    setSelectedFacets(new Set());
  }

  // Simulate filtered docs
  const filteredDocs = selectedFacets.size === 0
    ? allDocs
    : allDocs.filter((doc) => {
        for (const key of selectedFacets) {
          const [groupName, value] = key.split(":");
          const field = doc.raw.fields[groupName];
          if (Array.isArray(field) && field.includes(value)) return true;
          if (field === value) return true;
        }
        return false;
      });

  return (
    <div
      style={{
        display: "flex",
        gap: "24px",
        fontFamily: "system-ui, sans-serif",
        color: isDark ? "#e2e8f0" : "#1e293b",
        background: isDark ? "#0f172a" : "#ffffff",
        padding: "24px",
        borderRadius: "12px",
      }}
    >
      {/* Sidebar */}
      <aside style={{ width: "220px", flexShrink: 0 }}>
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            marginBottom: "16px",
          }}
        >
          <span style={{ fontSize: "12px", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em" }}>
            Filters
          </span>
          {selectedFacets.size > 0 && (
            <button
              onClick={clearAll}
              style={{
                fontSize: "11px",
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

        {initialGroups.map((group) => (
          <div key={group.name} style={{ marginBottom: "16px" }}>
            <h4
              style={{
                fontSize: "11px",
                fontWeight: 700,
                textTransform: "uppercase",
                letterSpacing: "0.08em",
                color: isDark ? "#94a3b8" : "#64748b",
                marginBottom: "6px",
              }}
            >
              {group.label.text}
            </h4>
            {group.facets.map((facet) => {
              const isSelected = selectedFacets.has(`${group.name}:${facet.label}`);
              return (
                <button
                  key={facet.label}
                  onClick={() => toggle(group.name, facet.label)}
                  style={{
                    display: "flex",
                    width: "100%",
                    justifyContent: "space-between",
                    alignItems: "center",
                    padding: "6px 10px",
                    borderRadius: "6px",
                    border: "none",
                    fontSize: "13px",
                    cursor: "pointer",
                    background: isSelected
                      ? isDark ? "rgba(59,130,246,0.15)" : "rgba(79,70,229,0.08)"
                      : "transparent",
                    color: isSelected
                      ? isDark ? "#60a5fa" : "#4f46e5"
                      : isDark ? "#94a3b8" : "#64748b",
                    fontWeight: isSelected ? 600 : 400,
                    marginBottom: "2px",
                    textAlign: "left",
                  }}
                >
                  <span>
                    {isSelected && "✕ "}
                    {facet.label}
                  </span>
                  <span
                    style={{
                      fontSize: "10px",
                      padding: "1px 6px",
                      borderRadius: "8px",
                      background: isSelected
                        ? isDark ? "rgba(59,130,246,0.2)" : "rgba(79,70,229,0.12)"
                        : isDark ? "#1e293b" : "#f1f5f9",
                    }}
                  >
                    {facet.count}
                  </span>
                </button>
              );
            })}
          </div>
        ))}
      </aside>

      {/* Results */}
      <div style={{ flex: 1 }}>
        <p style={{ fontSize: "13px", color: isDark ? "#94a3b8" : "#64748b", marginBottom: "12px" }}>
          <strong style={{ color: isDark ? "#e2e8f0" : "#1e293b" }}>{filteredDocs.length}</strong> results
          {selectedFacets.size > 0 && (
            <span> · {selectedFacets.size} filter{selectedFacets.size > 1 ? "s" : ""} active</span>
          )}
        </p>
        <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
          {filteredDocs.map((doc) => (
            <div
              key={doc.url}
              style={{
                padding: "12px",
                borderRadius: "8px",
                border: `1px solid ${isDark ? "#1e293b" : "#e2e8f0"}`,
                background: isDark ? "#1e293b40" : "#f8fafc",
              }}
            >
              <div style={{ fontWeight: 600, fontSize: "14px" }}>{doc.title}</div>
              <div style={{ fontSize: "12px", color: isDark ? "#94a3b8" : "#64748b", marginTop: "2px" }}>
                {doc.description.slice(0, 100)}...
              </div>
            </div>
          ))}
          {filteredDocs.length === 0 && (
            <div style={{ textAlign: "center", padding: "32px", color: isDark ? "#475569" : "#94a3b8" }}>
              No results match the selected filters.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

const meta: Meta<typeof FacetSidebar> = {
  title: "Hooks/useTuringFacets",
  component: FacetSidebar,
  tags: ["autodocs"],
  parameters: {
    layout: "fullscreen",
    docs: {
      description: {
        component:
          "Interactive demo simulating `useTuringFacets`. Click facets to toggle them — the result list filters accordingly.",
      },
    },
  },
};

export default meta;
type Story = StoryObj<typeof FacetSidebar>;

export const CreatureFacets: Story = {
  name: "🐉 Creature Facets",
  args: {
    facetGroups: creatureFacets,
    documents: resolveDocsMock(creatureDocuments),
    theme: "light",
  },
};

export const MissionFacets: Story = {
  name: "🚀 Mission Facets (Dark)",
  args: {
    facetGroups: missionFacets,
    documents: resolveDocsMock(missionDocuments),
    theme: "dark",
  },
};

export const VinylFacets: Story = {
  name: "🎵 Vinyl Facets",
  args: {
    facetGroups: vinylFacets,
    documents: resolveDocsMock(vinylDocuments),
    theme: "light",
  },
};
