import { fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";

const useLlmInstances = vi.fn();

vi.mock("@/api/queries/llm-instance.queries", () => ({
  useLlmInstances: () => useLlmInstances(),
}));

import { GatewayModelPicker } from "../gateway-model-picker";

function instance(id: string, modelName: string, vendorId: string, vendorTitle: string) {
  return {
    id,
    title: `${modelName} instance`,
    modelName,
    enabled: 1,
    turLLMVendor: { id: vendorId, title: vendorTitle, description: "" },
  };
}

function Harness({ initial = "" }: { initial?: string }) {
  const [value, setValue] = useState(initial);
  return (
    <>
      <GatewayModelPicker value={value} onChange={setValue} />
      <output data-testid="csv">{value}</output>
    </>
  );
}

describe("GatewayModelPicker", () => {
  it("starts in Any-model mode and hides the grid", () => {
    useLlmInstances.mockReturnValue({ data: [], isLoading: false });
    render(<Harness />);
    expect(screen.getByText("This key may call any model. Switch to “Choose models” to restrict it."))
      .toBeInTheDocument();
    expect(screen.queryByPlaceholderText("Search models…")).not.toBeInTheDocument();
  });

  it("reveals a vendor-grouped grid when Choose models is picked", () => {
    useLlmInstances.mockReturnValue({
      data: [
        instance("i-1", "gpt-4o", "openai", "OpenAI"),
        instance("i-2", "claude-3-5", "anthropic", "Anthropic"),
      ],
      isLoading: false,
    });
    render(<Harness />);
    fireEvent.click(screen.getByText("Choose models"));
    expect(screen.getByPlaceholderText("Search models…")).toBeInTheDocument();
    expect(screen.getByText("gpt-4o")).toBeInTheDocument();
    expect(screen.getByText("claude-3-5")).toBeInTheDocument();
    expect(screen.getByText("OpenAI")).toBeInTheDocument();
  });

  it("selecting a model card emits its model name as CSV", () => {
    useLlmInstances.mockReturnValue({
      data: [instance("i-1", "gpt-4o", "openai", "OpenAI")],
      isLoading: false,
    });
    render(<Harness />);
    fireEvent.click(screen.getByText("Choose models"));
    fireEvent.click(screen.getByText("gpt-4o"));
    expect(screen.getByTestId("csv")).toHaveTextContent("gpt-4o");
  });

  it("pre-selects the card matching an existing value", () => {
    useLlmInstances.mockReturnValue({
      data: [instance("i-1", "gpt-4o", "openai", "OpenAI")],
      isLoading: false,
    });
    render(<Harness initial="gpt-4o" />);
    // Choose mode is active because the value is non-empty; the card carries the
    // instance title (unique — the pill shows only the model name).
    const card = screen.getByText("gpt-4o instance").closest("button");
    expect(card).toHaveAttribute("aria-pressed", "true");
  });

  it("filters the grid by search query", () => {
    useLlmInstances.mockReturnValue({
      data: [
        instance("i-1", "gpt-4o", "openai", "OpenAI"),
        instance("i-2", "claude-3-5", "anthropic", "Anthropic"),
      ],
      isLoading: false,
    });
    render(<Harness />);
    fireEvent.click(screen.getByText("Choose models"));
    fireEvent.change(screen.getByPlaceholderText("Search models…"), {
      target: { value: "claude" },
    });
    expect(screen.getByText("claude-3-5")).toBeInTheDocument();
    expect(screen.queryByText("gpt-4o")).not.toBeInTheDocument();
  });
});
