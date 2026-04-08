import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { sampleSortOptions } from "../__mocks__/fixtures";
import type { TurSortOption } from "../../core/types";

/**
 * # useTuringSortOptions
 *
 * Fetches available sort options for the current site. Returns built-in sorts
 * (relevance, newest, oldest) plus any custom sorts from the Turing admin console.
 *
 * ## Key Features
 * - `sortOptions`: Array of `{ value, label }` objects
 * - `isLoading`: Loading state
 * - `error`: Error message if fetch fails
 * - `refresh()`: Re-fetch sort options
 *
 * ## Usage
 * ```tsx
 * const { sortOptions, isLoading } = useTuringSortOptions();
 *
 * <select onChange={(e) => setSort(e.target.value)}>
 *   {sortOptions.map(opt => (
 *     <option key={opt.value} value={opt.value}>{opt.label}</option>
 *   ))}
 * </select>
 * ```
 */

function SortOptionsDemo({
  options,
  style: theme,
}: {
  options: TurSortOption[];
  style: "select" | "radio" | "pills";
}) {
  const [selected, setSelected] = useState(options[0]?.value ?? "");

  return (
    <div style={{ fontFamily: "system-ui, sans-serif" }}>
      <p style={{ fontSize: "12px", color: "#94a3b8", marginBottom: "12px" }}>
        Active sort: <code style={{ color: "#6d28d9" }}>{selected}</code>
      </p>

      {theme === "select" && (
        <select
          value={selected}
          onChange={(e) => setSelected(e.target.value)}
          style={{
            padding: "8px 32px 8px 12px",
            fontSize: "14px",
            borderRadius: "8px",
            border: "2px solid #e2e8f0",
            outline: "none",
            cursor: "pointer",
            appearance: "auto",
          }}
        >
          {options.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      )}

      {theme === "radio" && (
        <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
          {options.map((opt) => (
            <label
              key={opt.value}
              style={{
                display: "flex",
                alignItems: "center",
                gap: "8px",
                padding: "8px 12px",
                borderRadius: "8px",
                border: opt.value === selected ? "2px solid #4f46e5" : "1px solid #e2e8f0",
                background: opt.value === selected ? "#eef2ff" : "white",
                cursor: "pointer",
                fontSize: "14px",
              }}
            >
              <input
                type="radio"
                name="sort"
                value={opt.value}
                checked={opt.value === selected}
                onChange={() => setSelected(opt.value)}
                style={{ accentColor: "#4f46e5" }}
              />
              <span style={{ fontWeight: opt.value === selected ? 600 : 400 }}>
                {opt.label}
              </span>
              <span style={{ marginLeft: "auto", fontSize: "11px", color: "#94a3b8", fontFamily: "monospace" }}>
                {opt.value}
              </span>
            </label>
          ))}
        </div>
      )}

      {theme === "pills" && (
        <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
          {options.map((opt) => (
            <button
              key={opt.value}
              onClick={() => setSelected(opt.value)}
              style={{
                padding: "6px 14px",
                borderRadius: "20px",
                border: "none",
                fontSize: "13px",
                fontWeight: opt.value === selected ? 600 : 400,
                cursor: "pointer",
                background: opt.value === selected
                  ? "linear-gradient(135deg, #2563eb, #4f46e5)"
                  : "#f1f5f9",
                color: opt.value === selected ? "white" : "#475569",
              }}
            >
              {opt.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

const meta: Meta<typeof SortOptionsDemo> = {
  title: "Hooks/useTuringSortOptions",
  component: SortOptionsDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Interactive sort options demo. Switch between select, radio, and pill styles to see how sort options can be presented.",
      },
    },
  },
};

export default meta;
type Story = StoryObj<typeof SortOptionsDemo>;

export const SelectDropdown: Story = {
  name: "Select Dropdown",
  args: {
    options: sampleSortOptions,
    style: "select",
  },
};

export const RadioGroup: Story = {
  name: "Radio Group",
  args: {
    options: sampleSortOptions,
    style: "radio",
  },
};

export const PillSelector: Story = {
  name: "Pill Selector",
  args: {
    options: sampleSortOptions,
    style: "pills",
  },
};
