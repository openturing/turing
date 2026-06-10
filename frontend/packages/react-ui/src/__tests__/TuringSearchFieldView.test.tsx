import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import {
  TuringSearchFieldView,
  type TuringSearchFieldViewContextValue,
} from "../TuringSearchFieldView";

/**
 * T307 — headless-contract render tests for the search-field view split out of
 * `@viglet/turing-react-sdk`'s `TuringSearchField`. They assert the structure,
 * the context-driven sub-components, and the callbacks — never styling (the
 * baked Tailwind defaults were stripped as part of the split).
 */

function makeState(
  overrides: Partial<TuringSearchFieldViewContextValue> = {},
): TuringSearchFieldViewContextValue {
  return {
    inputValue: "",
    suggestions: [],
    filteredHistory: [],
    showDropdown: false,
    onInputChange: vi.fn(),
    onFocus: vi.fn(),
    onBlur: vi.fn(),
    onSubmit: vi.fn(),
    onPick: vi.fn(),
    onRemoveHistory: vi.fn(),
    onClearHistory: vi.fn(),
    ...overrides,
  };
}

describe("TuringSearchFieldView.Input", () => {
  it("is controlled by inputValue and reports changes", () => {
    const onInputChange = vi.fn();
    render(
      <TuringSearchFieldView {...makeState({ inputValue: "jazz", onInputChange })}>
        <TuringSearchFieldView.Input placeholder="Search" />
      </TuringSearchFieldView>,
    );
    const input = screen.getByRole("searchbox") as HTMLInputElement;
    expect(input.value).toBe("jazz");
    fireEvent.change(input, { target: { value: "rock" } });
    expect(onInputChange).toHaveBeenCalledWith("rock");
  });

  it("submits on Enter and forwards focus/blur", () => {
    const onSubmit = vi.fn();
    const onFocus = vi.fn();
    const onBlur = vi.fn();
    render(
      <TuringSearchFieldView {...makeState({ onSubmit, onFocus, onBlur })}>
        <TuringSearchFieldView.Input />
      </TuringSearchFieldView>,
    );
    const input = screen.getByRole("searchbox");
    fireEvent.focus(input);
    expect(onFocus).toHaveBeenCalled();
    fireEvent.keyDown(input, { key: "Enter" });
    expect(onSubmit).toHaveBeenCalled();
    fireEvent.blur(input);
    expect(onBlur).toHaveBeenCalled();
  });
});

describe("TuringSearchFieldView.Button", () => {
  it("calls onSubmit on click", () => {
    const onSubmit = vi.fn();
    render(
      <TuringSearchFieldView {...makeState({ onSubmit })}>
        <TuringSearchFieldView.Button>Go</TuringSearchFieldView.Button>
      </TuringSearchFieldView>,
    );
    fireEvent.click(screen.getByText("Go"));
    expect(onSubmit).toHaveBeenCalled();
  });
});

describe("TuringSearchFieldView.Dropdown", () => {
  it("renders suggestions (priority) and picks them", () => {
    const onPick = vi.fn();
    render(
      <TuringSearchFieldView
        {...makeState({ suggestions: ["alpha", "beta"], showDropdown: true, onPick })}
      >
        <TuringSearchFieldView.Dropdown />
      </TuringSearchFieldView>,
    );
    expect(screen.getAllByRole("option")).toHaveLength(2);
    fireEvent.click(screen.getByText("beta"));
    expect(onPick).toHaveBeenCalledWith("beta");
  });

  it("falls back to history when there are no suggestions", () => {
    const onRemoveHistory = vi.fn();
    const onClearHistory = vi.fn();
    render(
      <TuringSearchFieldView
        {...makeState({
          filteredHistory: ["past query"],
          showDropdown: true,
          onRemoveHistory,
          onClearHistory,
        })}
      >
        <TuringSearchFieldView.Dropdown historyLabel="Recent" clearAllLabel="Wipe" />
      </TuringSearchFieldView>,
    );
    expect(screen.getByText("Recent")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Wipe"));
    expect(onClearHistory).toHaveBeenCalled();
    fireEvent.click(screen.getByTitle("Remove"));
    expect(onRemoveHistory).toHaveBeenCalledWith("past query");
  });

  it("honors render props for suggestions and history", () => {
    render(
      <TuringSearchFieldView
        {...makeState({ suggestions: ["x"], showDropdown: true })}
      >
        <TuringSearchFieldView.Dropdown
          renderSuggestion={(term, onClick) => (
            <button key={term} data-testid="custom-sugg" onClick={onClick}>
              {term}
            </button>
          )}
        />
      </TuringSearchFieldView>,
    );
    expect(screen.getByTestId("custom-sugg")).toBeInTheDocument();
  });

  it("renders nothing when closed with no suggestions", () => {
    const { container } = render(
      <TuringSearchFieldView {...makeState({ showDropdown: false })}>
        <TuringSearchFieldView.Dropdown />
      </TuringSearchFieldView>,
    );
    expect(container.querySelector('[role="listbox"]')).toBeNull();
  });
});

describe("TuringSearchFieldView context guard", () => {
  it("throws when a sub-component is used outside the Root", () => {
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});
    expect(() => render(<TuringSearchFieldView.Button>x</TuringSearchFieldView.Button>)).toThrow(
      /must be used inside/,
    );
    spy.mockRestore();
  });
});
