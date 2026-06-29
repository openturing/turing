import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import {
  TuringComparisonTable,
  TuringSpecCard,
  TuringConfigurator,
  ANSWER_AS_APP_COMPONENTS,
  formatTypedValue,
} from "../TuringAnswerAsApp";

/**
 * T442 — headless-contract tests for the answer-as-an-app generative components:
 * type-driven cell rendering, the respond() round-trip on row select / action
 * click / configurator submit, and the formatTypedValue helper. Behavior +
 * structure only (no styling).
 */

describe("formatTypedValue", () => {
  it("formats currency from the 'amount,ISO' payload", () => {
    expect(formatTypedValue("150.00,BRL", "currency", "en-US")).toContain("150");
  });
  it("renders booleans as check marks", () => {
    expect(formatTypedValue(true, "boolean")).toBe("✓");
    expect(formatTypedValue(false, "boolean")).toBe("✗");
  });
  it("falls back to the raw string for unparseable values", () => {
    expect(formatTypedValue("n/a", "number")).toBe("n/a");
  });
  it("returns empty string for null/undefined", () => {
    expect(formatTypedValue(null, "string")).toBe("");
    expect(formatTypedValue(undefined, "currency")).toBe("");
  });
});

describe("TuringComparisonTable", () => {
  const props = {
    title: "Compare",
    columns: [
      { key: "name", label: "Name", type: "string" as const },
      { key: "price", label: "Price", type: "currency" as const },
    ],
    rows: [
      { name: "Alpha", price: "10.00,USD" },
      { name: "Beta", price: "20.00,USD" },
    ],
  };

  it("renders a row per result and headers per column", () => {
    render(<TuringComparisonTable props={props} respond={() => {}} />);
    expect(screen.getByText("Compare")).toBeTruthy();
    expect(screen.getByText("Alpha")).toBeTruthy();
    expect(screen.getByText("Beta")).toBeTruthy();
    expect(screen.getByText("Name")).toBeTruthy();
  });

  it("responds with the selected row + index", () => {
    const respond = vi.fn();
    render(<TuringComparisonTable props={props} respond={respond} selectLabel="Pick" />);
    const buttons = screen.getAllByText("Pick");
    fireEvent.click(buttons[1]);
    expect(respond).toHaveBeenCalledWith({ selectedRow: props.rows[1], index: 1 });
  });
});

describe("TuringSpecCard", () => {
  it("renders typed fields and responds with the chosen action", () => {
    const respond = vi.fn();
    render(
      <TuringSpecCard
        props={{
          title: "Widget",
          fields: [{ label: "Price", value: "9.99,USD", type: "currency" }],
          actions: [{ label: "Add to cart", value: "add_to_cart" }],
        }}
        respond={respond}
      />,
    );
    expect(screen.getByText("Widget")).toBeTruthy();
    expect(screen.getByText("Price")).toBeTruthy();
    fireEvent.click(screen.getByText("Add to cart"));
    expect(respond).toHaveBeenCalledWith({ action: "add_to_cart" });
  });
});

describe("TuringConfigurator", () => {
  it("collects control values and responds on submit", () => {
    const respond = vi.fn();
    render(
      <TuringConfigurator
        props={{
          title: "Refine",
          submitLabel: "Go",
          controls: [
            { key: "max", label: "Max price", type: "slider", min: 0, max: 100, step: 1 },
            { key: "color", label: "Color", type: "select", options: ["red", "blue"] },
          ],
        }}
        respond={respond}
      />,
    );
    fireEvent.change(screen.getByLabelText("Max price"), { target: { value: "42" } });
    fireEvent.change(screen.getByLabelText("Color"), { target: { value: "blue" } });
    fireEvent.click(screen.getByText("Go"));
    expect(respond).toHaveBeenCalledWith({ values: { max: 42, color: "blue" } });
  });
});

describe("ANSWER_AS_APP_COMPONENTS", () => {
  it("maps the built-in client-tool names to components", () => {
    expect(Object.keys(ANSWER_AS_APP_COMPONENTS).sort()).toEqual([
      "comparison_table",
      "configurator",
      "spec_card",
    ]);
  });
});
