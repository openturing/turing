import { fireEvent, render, screen, within } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, it } from "vitest";

import { LlmModelMultiSelect } from "../llm-model-multi-select";
import type { TurLlmModelOption } from "@/models/llm/llm-model-option.model.ts";

const OPTIONS: TurLlmModelOption[] = [
  { id: "gpt-4o", label: "GPT-4o" },
  { id: "gpt-4o-mini", label: "GPT-4o mini" },
];

function Harness({
  initial = [] as string[],
  initialDefault = "",
}: {
  initial?: string[];
  initialDefault?: string;
}) {
  const [selected, setSelected] = useState<string[]>(initial);
  const [def, setDef] = useState(initialDefault);
  return (
    <>
      <LlmModelMultiSelect
        selected={selected}
        defaultModel={def}
        options={OPTIONS}
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
});
