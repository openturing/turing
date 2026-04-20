import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { samplePagination, simplePagination } from "../__mocks__/fixtures";
import type { TurPaginationItem } from "../../core/types";

/**
 * # useTuringPagination
 *
 * Provides pagination data with action helpers.
 * Reads from the shared search store (requires `urlSync` on TuringProvider).
 *
 * ## Key Features
 * - `pages`: Array of PaginationPage objects with `select()` action
 * - `currentPage`, `pageCount`, `totalCount`: Numeric metadata
 * - `hasPages`: Quick boolean check
 * - Each page has `isCurrent`, `isEllipsis`, and `type` for rendering logic
 *
 * ## Usage
 * ```tsx
 * const { pages, hasPages, currentPage, totalCount } = useTuringPagination();
 *
 * if (!hasPages) return null;
 *
 * return (
 *   <nav>
 *     {pages.map((page, i) => (
 *       <button
 *         key={`${page.type}-${i}`}
 *         onClick={page.select}
 *         disabled={page.isCurrent}
 *       >
 *         {page.label}
 *       </button>
 *     ))}
 *   </nav>
 * );
 * ```
 */

function PaginationDemo({
  items,
  style: theme,
}: {
  items: TurPaginationItem[];
  style: "default" | "pills" | "minimal";
}) {
  const [currentPage, setCurrentPage] = useState(1);

  const navSymbols: Record<string, string> = {
    FIRST: "«",
    PREVIOUS: "‹",
    NEXT: "›",
    LAST: "»",
  };

  return (
    <div style={{ fontFamily: "system-ui, sans-serif" }}>
      <p style={{ fontSize: "13px", color: "#64748b", marginBottom: "12px" }}>
        Current page: <strong>{currentPage}</strong> · Click to navigate
      </p>
      <nav style={{ display: "flex", gap: "4px", alignItems: "center" }}>
        {items.map((item, i) => {
          const isCurrent = item.page === currentPage && item.type !== "FIRST" && item.type !== "LAST"
            && item.type !== "PREVIOUS" && item.type !== "NEXT";
          const isEllipsis = item.type === "ELLIPSIS" || item.text === "...";
          const isNav = ["FIRST", "PREVIOUS", "NEXT", "LAST"].includes(item.type);

          if (isEllipsis) {
            return <span key={`e-${i}`} style={{ padding: "0 8px", color: "#94a3b8" }}>…</span>;
          }

          const getButtonStyle = (): React.CSSProperties => {
            const base: React.CSSProperties = {
              display: "inline-flex",
              alignItems: "center",
              justifyContent: "center",
              cursor: isCurrent ? "default" : "pointer",
              border: "none",
              fontFamily: "monospace",
              transition: "all 0.15s ease",
            };

            if (theme === "pills") {
              return {
                ...base,
                minWidth: "36px",
                height: "36px",
                borderRadius: "18px",
                fontSize: isNav ? "16px" : "13px",
                fontWeight: isCurrent ? 700 : 500,
                background: isCurrent ? "linear-gradient(135deg, #2563eb, #4f46e5)" : "#f1f5f9",
                color: isCurrent ? "white" : "#475569",
              };
            }

            if (theme === "minimal") {
              return {
                ...base,
                minWidth: "32px",
                height: "32px",
                borderRadius: "0",
                fontSize: isNav ? "14px" : "13px",
                fontWeight: isCurrent ? 700 : 400,
                background: "transparent",
                color: isCurrent ? "#4f46e5" : "#475569",
                borderBottom: isCurrent ? "2px solid #4f46e5" : "2px solid transparent",
              };
            }

            // Default
            return {
              ...base,
              minWidth: "36px",
              height: "36px",
              borderRadius: "8px",
              fontSize: isNav ? "16px" : "13px",
              fontWeight: isCurrent ? 700 : 500,
              background: isCurrent ? "#4f46e5" : "transparent",
              color: isCurrent ? "white" : "#475569",
            };
          };

          return (
            <button
              key={`${item.type}-${item.page}-${i}`}
              style={getButtonStyle()}
              disabled={isCurrent}
              onClick={() => {
                if (!isCurrent && item.page > 0) setCurrentPage(item.page);
              }}
            >
              {isNav ? navSymbols[item.type] : item.text}
            </button>
          );
        })}
      </nav>
    </div>
  );
}

const meta: Meta<typeof PaginationDemo> = {
  title: "Hooks/useTuringPagination",
  component: PaginationDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Interactive pagination demo. Click page numbers to navigate. This simulates the `useTuringPagination` hook behavior.",
      },
    },
  },
};

export default meta;
type Story = StoryObj<typeof PaginationDemo>;

export const DefaultStyle: Story = {
  name: "Default Style",
  args: {
    items: samplePagination,
    style: "default",
  },
};

export const PillStyle: Story = {
  name: "Pill Style",
  args: {
    items: samplePagination,
    style: "pills",
  },
};

export const MinimalStyle: Story = {
  name: "Minimal / Underline",
  args: {
    items: samplePagination,
    style: "minimal",
  },
};

export const FewPages: Story = {
  name: "Simple (3 Pages)",
  args: {
    items: simplePagination,
    style: "default",
  },
};
