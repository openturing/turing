import { fireEvent, render, screen, within } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, it } from "vitest";

import { LlmModelMultiSelect } from "../llm-model-multi-select";
import type {
  TurLlmModelKind,
  TurLlmModelOption,
} from "@/models/llm/llm-model-option.model.ts";

const OPTIONS: TurLlmModelOption[] = [
  { id: "gpt-4o", label: "GPT-4o", kind: "CHAT" },
  { id: "gpt-4o-mini", label: "GPT-4o mini", kind: "CHAT" },
];

// Mixed-kind options exercise the badge + type filter.
const MIXED: TurLlmModelOption[] = [
  { id: "gpt-4o", label: "GPT-4o", kind: "CHAT" },
  { id: "text-embedding-3-small", label: "text-embedding-3-small", kind: "EMBEDDING" },
];

// Catalog-enriched options exercise the T779 metadata line + sort control.
const ENRICHED: TurLlmModelOption[] = [
  {
    id: "expensive", label: "Expensive", kind: "CHAT",
    metadata: {
      contextWindow: 128000, pricing: { inputPer1M: 10, outputPer1M: 30 },
      benchmarks: { intelligenceIndex: 80 }, tier: "Frontier",
    },
  },
  {
    id: "cheap", label: "Cheap", kind: "CHAT",
    metadata: {
      contextWindow: 32000, pricing: { inputPer1M: 0.5, outputPer1M: 1.5 },
      benchmarks: { intelligenceIndex: 40 }, tier: "Light",
    },
  },
];

function Harness({
  initial = [] as string[],
  initialDefault = "",
  options = OPTIONS,
  expectedKind,
}: {
  initial?: string[];
  initialDefault?: string;
  options?: TurLlmModelOption[];
  expectedKind?: TurLlmModelKind;
}) {
  const [selected, setSelected] = useState<string[]>(initial);
  const [def, setDef] = useState(initialDefault);
  return (
    <>
      <LlmModelMultiSelect
        selected={selected}
        defaultModel={def}
        options={options}
        expectedKind={expectedKind}
        onChange={(next, nextDefault) => {
          setSelected(next);
          setDef(nextDefault);
        }}
      />
      <output data-testid="selected">{selected.join(",")}</output>
      <output data-testid="default">{def}</output>
    </>
  );
}

describe("LlmModelMultiSelect", () => {
  it("shows the empty state when nothing is selected", () => {
    render(<Harness />);
    expect(
      screen.getByText("No models selected yet — add at least one."),
    ).toBeInTheDocument();
  });

  it("renders a row per selected model with the default badged", () => {
    render(<Harness initial={["gpt-4o", "gpt-4o-mini"]} initialDefault="gpt-4o" />);
    const rows = screen.getAllByRole("listitem");
    expect(rows).toHaveLength(2);
    // Only the default row carries the "Default" badge.
    expect(screen.getByText("Default")).toBeInTheDocument();
    const defaultRow = screen.getByText("GPT-4o").closest("li")!;
    expect(within(defaultRow).getByText("Default")).toBeInTheDocument();
  });

  // Each row exposes two icon buttons in order: [0] star (set default), [1] remove.
  const rowButtons = (row: HTMLElement) => within(row).getAllByRole("button");

  it("starring another model moves the default", () => {
    render(<Harness initial={["gpt-4o", "gpt-4o-mini"]} initialDefault="gpt-4o" />);
    const miniRow = screen.getByText("GPT-4o mini").closest("li")!;
    fireEvent.click(rowButtons(miniRow)[0]);
    expect(screen.getByTestId("default")).toHaveTextContent("gpt-4o-mini");
  });

  it("removing a model drops it from the selection", () => {
    render(<Harness initial={["gpt-4o", "gpt-4o-mini"]} initialDefault="gpt-4o" />);
    const miniRow = screen.getByText("GPT-4o mini").closest("li")!;
    fireEvent.click(rowButtons(miniRow)[1]);
    expect(screen.getByTestId("selected")).toHaveTextContent("gpt-4o");
    expect(screen.getByTestId("selected")).not.toHaveTextContent("gpt-4o-mini");
  });

  it("removing the default promotes the first remaining model", () => {
    render(<Harness initial={["gpt-4o", "gpt-4o-mini"]} initialDefault="gpt-4o" />);
    const defaultRow = screen.getByText("GPT-4o").closest("li")!;
    fireEvent.click(rowButtons(defaultRow)[1]);
    expect(screen.getByTestId("selected")).toHaveTextContent("gpt-4o-mini");
    expect(screen.getByTestId("default")).toHaveTextContent("gpt-4o-mini");
  });

  it("shows a kind badge on each selected model row", () => {
    render(<Harness initial={["gpt-4o"]} initialDefault="gpt-4o" />);
    const row = screen.getByRole("listitem");
    // The mock i18n returns the raw key when no defaultValue is given.
    expect(within(row).getByText("forms.llm.modelKindChat")).toBeInTheDocument();
  });

  it("renders a type filter only when >=2 kinds are present and filters by kind", () => {
    render(<Harness options={MIXED} />);
    fireEvent.click(screen.getByLabelText("Add model")); // open the add popover
    const tablist = screen.getByRole("tablist");
    expect(within(tablist).getByRole("tab", { name: "All" })).toBeInTheDocument();
    // Both options list under "All".
    expect(screen.getByText("GPT-4o")).toBeInTheDocument();
    expect(screen.getByText("text-embedding-3-small")).toBeInTheDocument();
    // Switching to Embedding drops the chat option.
    fireEvent.click(within(tablist).getByRole("tab", { name: /modelKindEmbedding/ }));
    expect(screen.queryByText("GPT-4o")).not.toBeInTheDocument();
    expect(screen.getByText("text-embedding-3-small")).toBeInTheDocument();
  });

  it("defaults the type filter to expectedKind", () => {
    render(<Harness options={MIXED} expectedKind="CHAT" />);
    fireEvent.click(screen.getByLabelText("Add model"));
    // Chat is pre-selected, so only the chat option shows on open.
    expect(screen.getByText("GPT-4o")).toBeInTheDocument();
    expect(screen.queryByText("text-embedding-3-small")).not.toBeInTheDocument();
  });

  it("shows catalog metadata (T779) on enriched options and hides the sort control without metadata", () => {
    const { rerender } = render(<Harness options={ENRICHED} />);
    fireEvent.click(screen.getByLabelText("Add model"));
    // Compact meta line renders literal chips (context / price).
    expect(screen.getByText(/128K ctx/)).toBeInTheDocument();
    expect(screen.getByText(/\$10\/\$30/)).toBeInTheDocument();
    // Frontier tier badge is present.
    expect(screen.getByText("Frontier")).toBeInTheDocument();
    // Sort control appears when options carry metadata.
    expect(screen.getByRole("combobox")).toBeInTheDocument();

    // A metadata-free list hides the sort control entirely.
    rerender(<Harness options={OPTIONS} />);
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  });

  it("sorts the list by cheapest when the price sort is chosen (T779)", () => {
    render(<Harness options={ENRICHED} />);
    fireEvent.click(screen.getByLabelText("Add model"));
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "price" } });
    // The two option rows in the dropdown, in DOM order.
    const labels = screen
      .getAllByText(/^(Cheap|Expensive)$/)
      .map((el) => el.textContent);
    expect(labels[0]).toBe("Cheap");
  });
});
