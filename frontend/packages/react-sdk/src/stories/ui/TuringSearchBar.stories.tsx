import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
import { TuringSearchBar } from "@viglet/turing-react-ui";

/**
 * # TuringSearchBar
 *
 * A simple, unstyled search bar with customizable input and button via render props.
 * Ideal for quick integration when you don't need autocomplete or history.
 *
 * ## Features
 * - Controlled input with form submission
 * - `renderInput` / `renderButton` for full appearance control
 * - Calls `onSearch(query)` on Enter or button click
 * - Falls back to plain `<input>` and `<button>` if no render props are provided
 *
 * ## When to use
 * Use `TuringSearchBar` for simple search forms. For autocomplete, history, and
 * dropdown support, use `TuringSearchField` instead.
 */
const meta: Meta<typeof TuringSearchBar> = {
  title: "UI Components/TuringSearchBar",
  component: TuringSearchBar,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Simple search bar with customizable input/button. Uses render props for full control over the appearance.",
      },
    },
  },
  argTypes: {
    onSearch: {
      description: "Callback fired when the user submits a search",
      action: "searched",
    },
    placeholder: {
      description: "Placeholder text for the input",
      control: "text",
    },
    defaultQuery: {
      description: "Pre-fill the input with a default query",
      control: "text",
    },
  },
};

export default meta;
type Story = StoryObj<typeof TuringSearchBar>;

/**
 * ## Default (Unstyled)
 *
 * Without render props, the component renders a plain `<input>` and `<button>`.
 * This is the simplest usage — just provide `onSearch`.
 */
export const Default: Story = {
  args: {
    onSearch: fn(),
    placeholder: "Search...",
  },
};

/**
 * ## With Custom Styled Input
 *
 * Use `renderInput` to fully control the input element's appearance.
 * The render prop receives all necessary props (value, onChange, ref, etc.)
 */
export const StyledInput: Story = {
  name: "Styled Input",
  args: {
    onSearch: fn(),
    placeholder: "Search products...",
    renderInput: (props) => (
      <input
        {...props}
        style={{
          padding: "10px 16px",
          fontSize: "16px",
          border: "2px solid #6366f1",
          borderRadius: "8px",
          outline: "none",
          width: "300px",
        }}
      />
    ),
  },
};

/**
 * ## With Custom Button
 *
 * Use `renderButton` to replace the default submit button.
 * The render prop receives `{ onClick }` to trigger the search.
 */
export const CustomButton: Story = {
  name: "Custom Button",
  args: {
    onSearch: fn(),
    placeholder: "Search creatures...",
    renderInput: (props) => (
      <input
        {...props}
        style={{
          padding: "10px 16px",
          fontSize: "14px",
          border: "1px solid #e2e8f0",
          borderRadius: "8px 0 0 8px",
          outline: "none",
          width: "260px",
        }}
      />
    ),
    renderButton: ({ onClick }) => (
      <button
        onClick={onClick}
        style={{
          padding: "10px 20px",
          fontSize: "14px",
          background: "linear-gradient(135deg, #2563eb, #4f46e5)",
          color: "white",
          border: "none",
          borderRadius: "0 8px 8px 0",
          cursor: "pointer",
          fontWeight: 600,
        }}
      >
        🔍 Search
      </button>
    ),
  },
};

/**
 * ## Pre-filled Query
 *
 * Pass `defaultQuery` to pre-fill the search input.
 * Useful when navigating back to a search page with a saved query.
 */
export const PrefilledQuery: Story = {
  name: "Pre-filled Query",
  args: {
    onSearch: fn(),
    defaultQuery: "dragon",
    placeholder: "Search...",
  },
};

/**
 * ## Full Example (Card Style)
 *
 * A more complete example showing the search bar in a card layout,
 * similar to what you'd see in a real application header.
 */
export const CardStyle: Story = {
  name: "Card Style",
  args: {
    onSearch: fn(),
    placeholder: "What are you looking for?",
  },
  render: (args) => (
    <div
      style={{
        padding: "24px",
        background: "white",
        borderRadius: "16px",
        boxShadow: "0 4px 24px rgba(0,0,0,0.08)",
        maxWidth: "480px",
      }}
    >
      <h3 style={{ margin: "0 0 12px", fontSize: "18px", fontWeight: 700 }}>
        🔍 Enterprise Search
      </h3>
      <TuringSearchBar
        {...args}
        className="search-bar"
        renderInput={(props) => (
          <input
            {...props}
            style={{
              width: "100%",
              padding: "12px 16px",
              fontSize: "15px",
              border: "2px solid #e2e8f0",
              borderRadius: "10px",
              outline: "none",
              marginBottom: "8px",
              boxSizing: "border-box",
            }}
          />
        )}
        renderButton={({ onClick }) => (
          <button
            onClick={onClick}
            style={{
              width: "100%",
              padding: "10px",
              fontSize: "14px",
              fontWeight: 600,
              background: "linear-gradient(135deg, #2563eb, #4f46e5)",
              color: "white",
              border: "none",
              borderRadius: "10px",
              cursor: "pointer",
            }}
          >
            Search
          </button>
        )}
      />
    </div>
  ),
};
