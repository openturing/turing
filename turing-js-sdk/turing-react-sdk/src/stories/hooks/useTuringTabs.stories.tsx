import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { resolveDocsMock, creatureDocuments, missionDocuments, vinylDocuments } from "../__mocks__/fixtures";
import type { ResolvedDocument } from "../../core/types";

/**
 * # useTuringTabs
 *
 * Manages tab-based search navigation — handles fq[] filters, group params,
 * rows, and sort when tabs change. Uses implicit params to keep URLs clean.
 *
 * ## Key Features
 * - `tabs`: Enriched tab objects with `isActive`, `select()`, and `count`
 * - `activeTab`: The currently active tab
 * - `isGrouped`: Whether results should be shown as groups
 * - `documents` / `groups`: Data for the active tab
 * - Implicit params keep URLs clean (no `group=` or `rows=` in URL)
 *
 * ## Tab Definition
 * ```tsx
 * const TABS = [
 *   { label: "All",     group: "templateName", rows: 3 },
 *   { label: "Courses", filter: "templateName:cursos" },
 *   { label: "News",    filter: "templateName:noticias" },
 * ];
 * ```
 *
 * This story simulates the tabs behavior with sample data.
 */

interface TabDef {
  label: string;
  icon: string;
  documents: ResolvedDocument[];
}

function TabsDemo({ tabs: tabDefs }: { tabs: TabDef[] }) {
  const [activeIdx, setActiveIdx] = useState(0);
  const activeTab = tabDefs[activeIdx];

  return (
    <div style={{ fontFamily: "system-ui, sans-serif", maxWidth: "700px" }}>
      {/* Tab bar */}
      <div
        style={{
          display: "flex",
          gap: "0",
          borderBottom: "2px solid #e2e8f0",
          marginBottom: "16px",
        }}
      >
        {tabDefs.map((tab, i) => (
          <button
            key={tab.label}
            onClick={() => setActiveIdx(i)}
            style={{
              padding: "10px 20px",
              fontSize: "14px",
              fontWeight: i === activeIdx ? 600 : 400,
              color: i === activeIdx ? "#4f46e5" : "#64748b",
              background: "transparent",
              border: "none",
              borderBottom: i === activeIdx ? "2px solid #4f46e5" : "2px solid transparent",
              cursor: "pointer",
              marginBottom: "-2px",
              display: "flex",
              alignItems: "center",
              gap: "6px",
            }}
          >
            <span>{tab.icon}</span>
            {tab.label}
            <span
              style={{
                fontSize: "10px",
                padding: "1px 6px",
                borderRadius: "8px",
                background: i === activeIdx ? "#eef2ff" : "#f1f5f9",
                color: i === activeIdx ? "#4f46e5" : "#94a3b8",
              }}
            >
              {tab.documents.length}
            </span>
          </button>
        ))}
      </div>

      {/* Tab content */}
      <div>
        <p style={{ fontSize: "13px", color: "#64748b", marginBottom: "12px" }}>
          Showing <strong>{activeTab.documents.length}</strong> results for tab &quot;{activeTab.label}&quot;
        </p>
        <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
          {activeTab.documents.map((doc) => (
            <div
              key={doc.url}
              style={{
                display: "flex",
                gap: "12px",
                padding: "12px",
                borderRadius: "10px",
                border: "1px solid #e2e8f0",
                alignItems: "center",
              }}
            >
              {doc.image && (
                <img
                  src={doc.image}
                  alt={doc.title}
                  style={{
                    width: "60px",
                    height: "60px",
                    borderRadius: "8px",
                    objectFit: "cover",
                  }}
                />
              )}
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 600, fontSize: "14px" }}>{doc.title}</div>
                <div style={{ fontSize: "12px", color: "#64748b", marginTop: "2px" }}>
                  {doc.description.slice(0, 80)}...
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Code hint */}
      <div
        style={{
          marginTop: "24px",
          padding: "12px 16px",
          background: "#f8fafc",
          borderRadius: "8px",
          fontSize: "12px",
          fontFamily: "monospace",
          color: "#64748b",
        }}
      >
        <span style={{ color: "#6d28d9" }}>activeTab</span>.label = &quot;{activeTab.label}&quot;
        &nbsp;·&nbsp;
        <span style={{ color: "#6d28d9" }}>activeTab</span>.isActive = true
        &nbsp;·&nbsp;
        <span style={{ color: "#6d28d9" }}>documents</span>.length = {activeTab.documents.length}
      </div>
    </div>
  );
}

const meta: Meta<typeof TabsDemo> = {
  title: "Hooks/useTuringTabs",
  component: TabsDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "padded",
    docs: {
      description: {
        component:
          "Interactive tabs demo simulating `useTuringTabs`. Click tabs to switch datasets — this mirrors the behavior of tab-based search navigation.",
      },
    },
  },
};

export default meta;
type Story = StoryObj<typeof TabsDemo>;

export const ThreeThemes: Story = {
  name: "Three Themes",
  args: {
    tabs: [
      { label: "Creatures", icon: "🐉", documents: resolveDocsMock(creatureDocuments) },
      { label: "Missions", icon: "🚀", documents: resolveDocsMock(missionDocuments) },
      { label: "Vinyl", icon: "🎵", documents: resolveDocsMock(vinylDocuments) },
    ],
  },
};

export const TwoTabs: Story = {
  name: "Creatures Only",
  args: {
    tabs: [
      { label: "All Creatures", icon: "✨", documents: resolveDocsMock(creatureDocuments) },
      { label: "Dangerous Only", icon: "☠️", documents: resolveDocsMock(creatureDocuments).filter(d => (d.raw.fields.danger_level as number) >= 8) },
    ],
  },
};
