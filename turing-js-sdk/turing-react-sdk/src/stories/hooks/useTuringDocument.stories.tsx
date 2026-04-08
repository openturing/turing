import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import {
  resolveDocsMock,
  creatureDocuments,
  vinylDocuments,
} from "../__mocks__/fixtures";
import type { ResolvedDocument } from "../../core/types";

/**
 * # useTuringDocument
 *
 * Provides typed, normalized access to document custom fields.
 * Eliminates repetitive `Array.isArray()` checks and type coercion.
 *
 * ## Helpers
 * - `getString(field, fallback?)` → always returns a `string`
 * - `getArray(field)` → always returns `string[]`
 * - `getNumber(field, fallback?)` → always returns `number`
 * - `getBoolean(field)` → `true` / `false`
 * - `getDate(field)` → `Date | null`
 * - `getRaw<T>(field)` → raw value with type cast
 *
 * ## Why?
 * Turing API returns fields that can be `string | string[] | number | null`.
 * This hook normalizes access so your components stay clean:
 *
 * ```tsx
 * // Without hook:
 * const el = Array.isArray(fields.element) ? fields.element : fields.element ? [fields.element] : [];
 *
 * // With hook:
 * const el = d.getArray("element"); // always string[]
 * ```
 *
 * This story demonstrates the helper methods with creature and vinyl data.
 */

/* Simulated useTuringDocument (same logic as the real hook, without React context) */
function useDocHelper(doc: ResolvedDocument) {
  const fields = doc.raw.fields as Record<string, unknown>;

  const getString = (field: string, fallback = ""): string => {
    const val = fields[field];
    if (val == null) return fallback;
    if (Array.isArray(val)) return val.length > 0 ? String(val[0]) : fallback;
    return String(val);
  };

  const getArray = (field: string): string[] => {
    const val = fields[field];
    if (val == null) return [];
    if (Array.isArray(val)) return val.map(String);
    return [String(val)];
  };

  const getNumber = (field: string, fallback = 0): number => {
    const val = fields[field];
    if (val == null) return fallback;
    const num = Number(Array.isArray(val) ? val[0] : val);
    return Number.isNaN(num) ? fallback : num;
  };

  const getBoolean = (field: string): boolean => {
    const val = fields[field];
    if (val == null) return false;
    const v = Array.isArray(val) ? val[0] : val;
    return v === true || v === "true" || v === "1";
  };

  return { getString, getArray, getNumber, getBoolean, fields };
}

/* ── Document Inspector Component ── */

function DocumentInspector({
  documents,
  fieldsToShow,
}: {
  documents: ResolvedDocument[];
  fieldsToShow: string[];
}) {
  const [selectedIdx, setSelectedIdx] = useState(0);
  const doc = documents[selectedIdx];
  const d = useDocHelper(doc);

  return (
    <div style={{ fontFamily: "system-ui, sans-serif", maxWidth: "700px" }}>
      {/* Document selector */}
      <div style={{ display: "flex", gap: "6px", marginBottom: "16px", flexWrap: "wrap" }}>
        {documents.map((doc, i) => (
          <button
            key={doc.url}
            onClick={() => setSelectedIdx(i)}
            style={{
              padding: "6px 12px",
              borderRadius: "8px",
              border: i === selectedIdx ? "2px solid #4f46e5" : "1px solid #e2e8f0",
              background: i === selectedIdx ? "#eef2ff" : "white",
              cursor: "pointer",
              fontSize: "13px",
              fontWeight: i === selectedIdx ? 600 : 400,
              color: i === selectedIdx ? "#4f46e5" : "#475569",
            }}
          >
            {doc.title}
          </button>
        ))}
      </div>

      {/* Document card */}
      <div
        style={{
          border: "1px solid #e2e8f0",
          borderRadius: "12px",
          overflow: "hidden",
          background: "white",
        }}
      >
        {doc.image && (
          <img
            src={doc.image}
            alt={doc.title}
            style={{ width: "100%", height: "160px", objectFit: "cover" }}
          />
        )}
        <div style={{ padding: "16px" }}>
          <h3 style={{ margin: "0 0 4px", fontSize: "18px" }}>{doc.title}</h3>
          <p style={{ margin: "0 0 16px", fontSize: "13px", color: "#64748b" }}>
            {doc.description}
          </p>

          {/* Field helper table */}
          <table
            style={{
              width: "100%",
              borderCollapse: "collapse",
              fontSize: "13px",
            }}
          >
            <thead>
              <tr style={{ borderBottom: "2px solid #e2e8f0" }}>
                <th style={{ textAlign: "left", padding: "6px 8px", color: "#64748b" }}>Field</th>
                <th style={{ textAlign: "left", padding: "6px 8px", color: "#64748b" }}>Method</th>
                <th style={{ textAlign: "left", padding: "6px 8px", color: "#64748b" }}>Raw Value</th>
                <th style={{ textAlign: "left", padding: "6px 8px", color: "#64748b" }}>Resolved</th>
              </tr>
            </thead>
            <tbody>
              {fieldsToShow.map((field) => {
                const raw = d.fields[field];
                const isArrayField = Array.isArray(raw);
                const method = isArrayField ? "getArray" : typeof raw === "number" ? "getNumber" : typeof raw === "boolean" ? "getBoolean" : "getString";
                const resolved = isArrayField
                  ? d.getArray(field).join(", ")
                  : typeof raw === "number"
                    ? String(d.getNumber(field))
                    : typeof raw === "boolean"
                      ? String(d.getBoolean(field))
                      : d.getString(field, "—");
                return (
                  <tr key={field} style={{ borderBottom: "1px solid #f1f5f9" }}>
                    <td style={{ padding: "6px 8px", fontFamily: "monospace", color: "#6d28d9" }}>
                      {field}
                    </td>
                    <td style={{ padding: "6px 8px", fontFamily: "monospace", fontSize: "11px", color: "#94a3b8" }}>
                      {method}()
                    </td>
                    <td style={{ padding: "6px 8px", color: "#94a3b8", fontStyle: "italic" }}>
                      {JSON.stringify(raw)}
                    </td>
                    <td style={{ padding: "6px 8px", fontWeight: 500 }}>
                      {resolved}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

const meta: Meta<typeof DocumentInspector> = {
  title: "Hooks/useTuringDocument",
  component: DocumentInspector,
  tags: ["autodocs"],
  parameters: {
    layout: "padded",
    docs: {
      description: {
        component:
          "Interactive field inspector. Select a document to see how `useTuringDocument` normalizes raw API fields into typed accessors.",
      },
    },
  },
};

export default meta;
type Story = StoryObj<typeof DocumentInspector>;

export const CreatureFields: Story = {
  name: "🐉 Creature Fields",
  args: {
    documents: resolveDocsMock(creatureDocuments),
    fieldsToShow: ["element", "creature_type", "danger_level", "origin"],
  },
};

export const VinylFields: Story = {
  name: "🎵 Vinyl Fields",
  args: {
    documents: resolveDocsMock(vinylDocuments),
    fieldsToShow: ["artist", "genre", "condition", "price", "rating", "is_first_pressing", "year"],
  },
};
