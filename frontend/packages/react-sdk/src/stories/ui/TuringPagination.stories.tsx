import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
import { TuringPagination } from "@viglet/turing-react-ui";
import { samplePagination, simplePagination } from "../__mocks__/fixtures";

/**
 * # TuringPagination
 *
 * Renders pagination items from the Turing API response. Each item
 * includes its type (FIRST, PREVIOUS, PAGE, CURRENT, NEXT, LAST, ELLIPSIS)
 * and an `href` link that can be passed to `navigate()`.
 *
 * ## Features
 * - Automatic label formatting (FIRST → "First", PAGE → page number)
 * - `itemComponent` render prop for full visual control
 * - `CURRENT` items are disabled (active page)
 * - `ELLIPSIS` items are non-interactive
 * - Accessible: uses `<nav>` with `aria-label`, `aria-current="page"`
 */
const meta: Meta<typeof TuringPagination> = {
  title: "UI Components/TuringPagination",
  component: TuringPagination,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Headless pagination component. Renders Turing API pagination items with support for custom item rendering.",
      },
    },
  },
  argTypes: {
    onNavigate: {
      description: "Callback when a pagination link is clicked. Receives the href string.",
      action: "navigated",
    },
  },
};

export default meta;
type Story = StoryObj<typeof TuringPagination>;

/**
 * ## Default (Unstyled)
 *
 * Without `itemComponent`, renders basic `<button>` elements.
 * CURRENT page is disabled, ELLIPSIS shows "…".
 */
export const Default: Story = {
  args: {
    items: samplePagination,
    onNavigate: fn(),
  },
};

/**
 * ## Simple (3 pages)
 *
 * A compact pagination with just 3 pages and a Next button.
 */
export const Simple: Story = {
  name: "Simple (3 Pages)",
  args: {
    items: simplePagination,
    onNavigate: fn(),
  },
};

/**
 * ## Styled Pagination
 *
 * Custom `itemComponent` with pill-style buttons and gradient active state.
 * This mirrors the style used in the turing-marketplace projects.
 */
export const StyledPagination: Story = {
  name: "Styled (Pill Buttons)",
  args: {
    items: samplePagination,
    onNavigate: fn(),
    itemComponent: ({ item, isCurrent, isEllipsis, onClick, label }) => {
      if (isEllipsis) {
        return (
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              justifyContent: "center",
              width: "36px",
              height: "36px",
              color: "#94a3b8",
              fontWeight: 500,
            }}
          >
            …
          </span>
        );
      }

      const isNav = ["FIRST", "PREVIOUS", "NEXT", "LAST"].includes(item.type);
      const navSymbols: Record<string, string> = {
        FIRST: "«",
        PREVIOUS: "‹",
        NEXT: "›",
        LAST: "»",
      };

      return (
        <button
          onClick={onClick}
          disabled={isCurrent}
          style={{
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            minWidth: "36px",
            height: "36px",
            padding: "0 10px",
            borderRadius: "8px",
            border: "none",
            fontSize: isNav ? "18px" : "13px",
            fontWeight: isCurrent ? 700 : 500,
            fontFamily: "monospace",
            cursor: isCurrent ? "default" : "pointer",
            background: isCurrent
              ? "linear-gradient(135deg, #2563eb, #4f46e5)"
              : "transparent",
            color: isCurrent ? "white" : "#475569",
            transition: "all 0.15s ease",
          }}
        >
          {isNav ? navSymbols[item.type] : label}
        </button>
      );
    },
  },
};

/**
 * ## Dark Theme
 *
 * Pagination styled for dark backgrounds, as used in the Space Missions example.
 */
export const DarkTheme: Story = {
  name: "Dark Theme",
  args: {
    items: samplePagination,
    onNavigate: fn(),
    itemComponent: ({ item, isCurrent, isEllipsis, onClick, label }) => {
      if (isEllipsis) {
        return (
          <span style={{ color: "#475569", padding: "0 4px" }}>…</span>
        );
      }

      const isNav = ["FIRST", "PREVIOUS", "NEXT", "LAST"].includes(item.type);
      const navSymbols: Record<string, string> = {
        FIRST: "⏮",
        PREVIOUS: "◀",
        NEXT: "▶",
        LAST: "⏭",
      };

      return (
        <button
          onClick={onClick}
          disabled={isCurrent}
          style={{
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            minWidth: "32px",
            height: "32px",
            padding: "0 8px",
            borderRadius: "6px",
            border: isCurrent ? "1px solid #3b82f6" : "1px solid #1e293b",
            fontSize: "12px",
            fontWeight: isCurrent ? 700 : 400,
            fontFamily: "monospace",
            cursor: isCurrent ? "default" : "pointer",
            background: isCurrent ? "#1e3a5f" : "#0f172a",
            color: isCurrent ? "#60a5fa" : "#94a3b8",
          }}
        >
          {isNav ? navSymbols[item.type] : label}
        </button>
      );
    },
  },
  decorators: [
    (Story) => (
      <div
        style={{
          display: "flex",
          gap: "4px",
          alignItems: "center",
          background: "#020617",
          padding: "16px 24px",
          borderRadius: "12px",
        }}
      >
        <Story />
      </div>
    ),
  ],
};

/**
 * ## No Pagination
 *
 * When no items are provided, nothing is rendered.
 */
export const NoPagination: Story = {
  name: "No Pagination",
  args: {
    items: [],
    onNavigate: fn(),
  },
};
