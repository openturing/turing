import type { Meta, StoryObj } from "@storybook/react-vite";
import { TuringResultList } from "@viglet/turing-react-ui";
import {
  resolveDocsMock,
  creatureDocuments,
  missionDocuments,
  vinylDocuments,
} from "../__mocks__/fixtures";

/**
 * # TuringResultList
 *
 * Renders search results via a slot/render-prop pattern. You provide the
 * `itemComponent` to control how each result is displayed.
 *
 * ## Features
 * - Headless: zero built-in styles, full render control
 * - `emptyComponent` for "no results" state
 * - `loadingComponent` for loading state
 * - Each item receives `{ document, raw, index }`
 *
 * ## Pattern
 * ```tsx
 * <TuringResultList
 *   documents={documents}
 *   itemComponent={({ document }) => (
 *     <div>{document.title}</div>
 *   )}
 * />
 * ```
 */
const meta: Meta<typeof TuringResultList> = {
  title: "UI Components/TuringResultList",
  component: TuringResultList,
  tags: ["autodocs"],
  parameters: {
    layout: "padded",
    docs: {
      description: {
        component:
          "Headless result list component. Renders search results using a render-prop pattern for maximum customization.",
      },
    },
  },
};

export default meta;
type Story = StoryObj<typeof TuringResultList>;

/* ── Creature theme ── */

const creatureDocs = resolveDocsMock(creatureDocuments);

/**
 * ## Default (Creatures)
 *
 * Simple list rendering with the mythical creatures dataset.
 * Each item shows title, description, and custom fields from `doc.raw.fields`.
 */
export const CreaturesList: Story = {
  name: "🐉 Creatures List",
  args: {
    documents: creatureDocs,
    itemComponent: ({ document, index }) => (
      <div
        key={document.url}
        style={{
          display: "flex",
          gap: "16px",
          padding: "16px",
          borderBottom: "1px solid #e2e8f0",
          alignItems: "flex-start",
        }}
      >
        {document.image && (
          <img
            src={document.image}
            alt={document.title}
            style={{
              width: "80px",
              height: "60px",
              objectFit: "cover",
              borderRadius: "8px",
            }}
          />
        )}
        <div>
          <div style={{ fontWeight: 600, fontSize: "15px", marginBottom: "4px" }}>
            {index + 1}. {document.title}
          </div>
          <div style={{ color: "#64748b", fontSize: "13px", lineHeight: 1.5 }}>
            {document.description}
          </div>
          <div style={{ marginTop: "8px", display: "flex", gap: "6px" }}>
            {(document.raw.fields.element as string[] || []).map((el: string) => (
              <span
                key={el}
                style={{
                  padding: "2px 8px",
                  fontSize: "11px",
                  borderRadius: "12px",
                  background: "#ede9fe",
                  color: "#6d28d9",
                  fontWeight: 500,
                }}
              >
                {el}
              </span>
            ))}
          </div>
        </div>
      </div>
    ),
  },
};

/* ── Space missions theme ── */

const missionDocs = resolveDocsMock(missionDocuments);

/**
 * ## Space Missions Grid
 *
 * Card-grid layout showing space missions with outcome badges and metadata.
 */
export const MissionsGrid: Story = {
  name: "🚀 Missions Grid",
  args: {
    documents: missionDocs,
    className: "missions-grid",
    itemComponent: ({ document }) => {
      const fields = document.raw.fields;
      const outcomeColor =
        fields.outcome === "Success"
          ? "#10b981"
          : fields.outcome === "Failure"
            ? "#ef4444"
            : "#f59e0b";
      return (
        <div
          style={{
            display: "inline-block",
            width: "260px",
            margin: "8px",
            borderRadius: "12px",
            border: "1px solid #1e293b",
            background: "#0f172a",
            color: "white",
            overflow: "hidden",
            verticalAlign: "top",
          }}
        >
          <img
            src={document.image}
            alt={document.title}
            style={{ width: "100%", height: "140px", objectFit: "cover" }}
          />
          <div style={{ padding: "12px" }}>
            <div
              style={{
                display: "inline-block",
                padding: "2px 8px",
                fontSize: "10px",
                borderRadius: "12px",
                background: outcomeColor + "22",
                color: outcomeColor,
                fontWeight: 700,
                border: `1px solid ${outcomeColor}44`,
                marginBottom: "8px",
              }}
            >
              {fields.outcome as string}
            </div>
            <div style={{ fontWeight: 700, fontSize: "14px", fontFamily: "monospace" }}>
              {document.title}
            </div>
            <div style={{ color: "#94a3b8", fontSize: "12px", marginTop: "4px" }}>
              {document.description.slice(0, 100)}...
            </div>
            <div
              style={{
                display: "flex",
                gap: "12px",
                marginTop: "8px",
                fontSize: "10px",
                color: "#64748b",
                fontFamily: "monospace",
              }}
            >
              <span>📅 {fields.launch_date as string}</span>
              <span>⏱ {fields.duration as string}</span>
            </div>
          </div>
        </div>
      );
    },
  },
};

/* ── Vinyl theme ── */

const vinylDocs = resolveDocsMock(vinylDocuments);

/**
 * ## Vinyl Records
 *
 * Album card layout with cover art, artist info, rating, and price.
 */
export const VinylRecords: Story = {
  name: "🎵 Vinyl Records",
  args: {
    documents: vinylDocs,
    itemComponent: ({ document }) => {
      const fields = document.raw.fields;
      const stars = Math.round(fields.rating as number || 0);
      return (
        <div
          style={{
            display: "inline-block",
            width: "200px",
            margin: "8px",
            borderRadius: "12px",
            border: "1px solid #e2e8f0",
            background: "white",
            overflow: "hidden",
            verticalAlign: "top",
          }}
        >
          <img
            src={document.image}
            alt={document.title}
            style={{ width: "100%", aspectRatio: "1", objectFit: "cover" }}
          />
          <div style={{ padding: "12px" }}>
            <div style={{ fontWeight: 700, fontSize: "14px", lineHeight: 1.3 }}>
              {document.title}
            </div>
            <div style={{ color: "#64748b", fontSize: "12px", marginTop: "2px" }}>
              {fields.artist as string}
            </div>
            <div style={{ marginTop: "4px", fontSize: "13px" }}>
              {"★".repeat(stars)}{"☆".repeat(5 - stars)}
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", marginTop: "8px" }}>
              <span
                style={{
                  fontSize: "10px",
                  padding: "2px 6px",
                  borderRadius: "8px",
                  background: "#f0fdf4",
                  color: "#16a34a",
                }}
              >
                {fields.condition as string}
              </span>
              <span style={{ fontWeight: 700, color: "#d97706" }}>
                ${fields.price as number}
              </span>
            </div>
          </div>
        </div>
      );
    },
  },
};

/**
 * ## Empty State
 *
 * When no documents are returned, the `emptyComponent` is rendered.
 */
export const EmptyState: Story = {
  name: "Empty State",
  args: {
    documents: [],
    itemComponent: () => null,
    emptyComponent: () => (
      <div
        style={{
          padding: "48px",
          textAlign: "center",
          color: "#94a3b8",
        }}
      >
        <div style={{ fontSize: "48px", marginBottom: "12px" }}>🔍</div>
        <div style={{ fontWeight: 600, fontSize: "16px", marginBottom: "4px" }}>
          No results found
        </div>
        <div style={{ fontSize: "13px" }}>
          Try adjusting your search or clearing filters.
        </div>
      </div>
    ),
  },
};

/**
 * ## Loading State
 *
 * When `isLoading` is true, the `loadingComponent` is rendered instead of results.
 */
export const LoadingState: Story = {
  name: "Loading State",
  args: {
    documents: [],
    isLoading: true,
    itemComponent: () => null,
    loadingComponent: () => (
      <div
        style={{
          padding: "48px",
          textAlign: "center",
          color: "#94a3b8",
        }}
      >
        <div style={{ fontSize: "32px", marginBottom: "12px" }}>⏳</div>
        <div style={{ fontSize: "14px" }}>Searching...</div>
      </div>
    ),
  },
};
