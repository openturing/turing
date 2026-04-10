import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import {
  resolveDocsMock,
  missionDocuments,
  missionFacets,
} from "../__mocks__/fixtures";
import type { ResolvedDocument } from "../../core/types";

/**
 * # 🚀 Space Missions Showcase
 *
 * Full search page inspired by `turing-marketplace/space-missions` — a space exploration
 * catalog with agency badges, outcome indicators, and HUD-style cards.
 *
 * ## SDK Components Used
 * - `TuringProvider` + `useTuringFacets` for agency/outcome filtering
 * - `useTuringDocument` for typed field access (agency, outcome, launch_date, etc.)
 * - `TuringSearchField` for search with autocomplete
 * - `TuringResultList` for mission cards
 *
 * ## Theme Details
 * - Deep space dark theme (#020617 base)
 * - HUD-style card borders with corner accents
 * - Outcome color coding: Success → green, Failure → red, Ongoing → cyan
 * - Agency badges with brand colors (NASA → blue, ESA → sky)
 * - Monospace typography for a technical feel
 */

const outcomeColors: Record<string, string> = {
  Success: "#10b981",
  Failure: "#ef4444",
  Partial: "#f59e0b",
  Ongoing: "#06b6d4",
  Planned: "#6366f1",
};

const agencyColors: Record<string, string> = {
  NASA: "#3b82f6",
  ESA: "#38bdf8",
  Roscosmos: "#ef4444",
  SpaceX: "#94a3b8",
  JAXA: "#f43f5e",
};

function MissionCard({ doc }: { doc: ResolvedDocument }) {
  const fields = doc.raw.fields;
  const outcome = (fields.outcome as string) ?? "";
  const agencies = Array.isArray(fields.agency) ? (fields.agency as string[]) : fields.agency ? [fields.agency as string] : [];
  const launchDate = fields.launch_date as string ?? "";
  const duration = fields.duration as string ?? "";
  const crewSize = fields.crew_size as number ?? 0;
  const program = fields.program as string ?? "";
  const oc = outcomeColors[outcome] ?? "#64748b";

  return (
    <div
      style={{
        position: "relative",
        borderRadius: "12px",
        border: "1px solid #1e293b",
        background: "rgba(15,23,42,0.8)",
        overflow: "hidden",
        backdropFilter: "blur(4px)",
        cursor: "pointer",
        transition: "all 0.3s ease",
      }}
    >
      {/* HUD corners */}
      <div style={{ position: "absolute", top: 0, left: 0, width: "16px", height: "16px", borderTop: "1px solid rgba(59,130,246,0.3)", borderLeft: "1px solid rgba(59,130,246,0.3)", borderRadius: "12px 0 0 0" }} />
      <div style={{ position: "absolute", top: 0, right: 0, width: "16px", height: "16px", borderTop: "1px solid rgba(59,130,246,0.3)", borderRight: "1px solid rgba(59,130,246,0.3)", borderRadius: "0 12px 0 0" }} />

      {doc.image && (
        <div style={{ position: "relative", height: "160px", overflow: "hidden" }}>
          <img src={doc.image} alt={doc.title} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
          <div style={{ position: "absolute", inset: 0, background: "linear-gradient(to top, #0f172a, transparent)" }} />
          {outcome && (
            <div
              style={{
                position: "absolute",
                top: "8px",
                right: "8px",
                padding: "2px 10px",
                borderRadius: "12px",
                fontSize: "10px",
                fontFamily: "monospace",
                fontWeight: 700,
                background: oc + "22",
                color: oc,
                border: `1px solid ${oc}44`,
                backdropFilter: "blur(4px)",
              }}
            >
              {outcome}
            </div>
          )}
        </div>
      )}

      <div style={{ padding: "14px" }}>
        <h3
          style={{
            margin: "0 0 4px",
            fontSize: "14px",
            fontWeight: 700,
            fontFamily: "monospace",
            color: "#e2e8f0",
          }}
        >
          {doc.title}
        </h3>
        <p style={{ margin: "0 0 10px", fontSize: "11px", color: "#94a3b8", lineHeight: 1.5 }}>
          {doc.description.slice(0, 120)}...
        </p>

        {/* Meta info */}
        <div style={{ display: "flex", flexWrap: "wrap", gap: "10px", fontSize: "10px", fontFamily: "monospace", color: "#64748b", marginBottom: "8px" }}>
          {launchDate && <span>📅 {launchDate}</span>}
          {duration && <span>⏱ {duration}</span>}
          {crewSize > 0 && <span>👨‍🚀 {crewSize}</span>}
          {program && <span>📡 {program}</span>}
        </div>

        {/* Agency badges */}
        <div style={{ display: "flex", gap: "4px" }}>
          {agencies.map((a) => (
            <span
              key={a}
              style={{
                padding: "2px 8px",
                borderRadius: "10px",
                fontSize: "10px",
                fontWeight: 600,
                background: (agencyColors[a] ?? "#6366f1") + "20",
                color: agencyColors[a] ?? "#6366f1",
                border: `1px solid ${(agencyColors[a] ?? "#6366f1")}40`,
              }}
            >
              {a}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}

function SpaceMissionsPage() {
  const allDocs = resolveDocsMock(missionDocuments);
  const [query, setQuery] = useState("");
  const [selectedFacets, setSelectedFacets] = useState<Set<string>>(new Set());

  const filteredDocs = allDocs.filter((doc) => {
    if (query && !doc.title.toLowerCase().includes(query.toLowerCase()) &&
        !doc.description.toLowerCase().includes(query.toLowerCase())) {
      return false;
    }
    if (selectedFacets.size === 0) return true;
    for (const key of selectedFacets) {
      const [g, v] = key.split(":");
      const f = doc.raw.fields[g];
      if (Array.isArray(f) && f.includes(v)) return true;
      if (f === v) return true;
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
        background: "#020617",
        color: "#e2e8f0",
        fontFamily: "system-ui, sans-serif",
        position: "relative",
        overflow: "hidden",
      }}
    >
      {/* Starfield */}
      <div style={{ position: "absolute", inset: 0, background: "radial-gradient(circle at 30% 20%, rgba(59,130,246,0.05) 0%, transparent 50%), radial-gradient(circle at 70% 60%, rgba(99,102,241,0.03) 0%, transparent 50%)" }} />

      {/* Header */}
      <header
        style={{
          position: "relative",
          padding: "16px 24px",
          borderBottom: "1px solid #0f172a",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          backdropFilter: "blur(8px)",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <span style={{ fontSize: "20px" }}>🚀</span>
          <span style={{ fontWeight: 700, fontSize: "14px", fontFamily: "monospace", letterSpacing: "0.1em" }}>
            MISSION CONTROL
          </span>
        </div>
        <span style={{ fontSize: "11px", color: "#475569", fontFamily: "monospace" }}>Viglet Turing ES</span>
      </header>

      {/* Hero */}
      <section style={{ position: "relative", textAlign: "center", padding: "48px 24px 24px" }}>
        <div style={{ fontSize: "10px", color: "#3b82f6", textTransform: "uppercase", letterSpacing: "0.3em", fontFamily: "monospace", marginBottom: "8px" }}>
          ─── Explore the cosmos ───
        </div>
        <h1 style={{ margin: "0 0 8px", fontSize: "32px", fontWeight: 800, fontFamily: "monospace" }}>
          Space Missions
        </h1>
        <p style={{ color: "#64748b", maxWidth: "500px", margin: "0 auto 24px", fontSize: "13px" }}>
          Discover humanity&apos;s greatest achievements in space exploration — from the Moon landings to the outer planets.
        </p>

        <div style={{ maxWidth: "480px", margin: "0 auto" }}>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "8px",
              padding: "10px 16px",
              borderRadius: "10px",
              border: "1px solid #1e293b",
              background: "#0f172a",
            }}
          >
            <span>🔍</span>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search missions... apollo, voyager, mars..."
              style={{
                flex: 1,
                background: "transparent",
                border: "none",
                outline: "none",
                fontSize: "14px",
                fontFamily: "monospace",
                color: "#e2e8f0",
              }}
            />
          </div>
        </div>
      </section>

      {/* Content */}
      <main style={{ position: "relative", display: "flex", gap: "24px", maxWidth: "1000px", margin: "0 auto", padding: "0 24px 48px" }}>
        {/* Sidebar */}
        <aside style={{ width: "200px", flexShrink: 0 }}>
          <div style={{ fontSize: "10px", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.2em", fontFamily: "monospace", color: "#3b82f6", marginBottom: "12px" }}>
            Mission Filters
          </div>
          {missionFacets.map((group) => (
            <div
              key={group.name}
              style={{
                marginBottom: "12px",
                padding: "10px",
                borderRadius: "8px",
                border: "1px solid #1e293b",
                background: "rgba(15,23,42,0.6)",
              }}
            >
              <h4 style={{ fontSize: "9px", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.15em", color: "rgba(59,130,246,0.7)", fontFamily: "monospace", marginBottom: "6px" }}>
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
                      borderRadius: "4px",
                      border: isSelected ? "1px solid rgba(59,130,246,0.3)" : "none",
                      background: isSelected ? "rgba(59,130,246,0.15)" : "transparent",
                      color: isSelected ? "#60a5fa" : "#94a3b8",
                      fontSize: "11px",
                      fontFamily: "monospace",
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

        {/* Grid */}
        <div style={{ flex: 1 }}>
          <p style={{ fontSize: "12px", color: "#475569", fontFamily: "monospace", marginBottom: "12px" }}>
            <strong style={{ color: "#e2e8f0" }}>{filteredDocs.length}</strong> missions found
          </p>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))",
              gap: "16px",
            }}
          >
            {filteredDocs.map((doc) => (
              <MissionCard key={doc.url} doc={doc} />
            ))}
          </div>
          {filteredDocs.length === 0 && (
            <div style={{ textAlign: "center", padding: "48px 0", color: "#475569" }}>
              🛸 No missions found. Try a different query.
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

const meta: Meta<typeof SpaceMissionsPage> = {
  title: "Showcases/🚀 Space Missions",
  component: SpaceMissionsPage,
  tags: ["autodocs"],
  parameters: {
    layout: "fullscreen",
    docs: {
      description: {
        component:
          "Full search page demo inspired by `turing-marketplace/space-missions`. Features a deep-space theme with HUD cards, outcome badges, and agency filters.",
      },
    },
  },
};

export default meta;
type Story = StoryObj<typeof SpaceMissionsPage>;

export const MissionControl: Story = {
  name: "Mission Control",
};
