import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { TuringSourceChips, type TuringRagSource } from "../TuringSourceChips";

/**
 * T293 — headless-contract tests for the RAG source-chip renderer: grouping by
 * source, confidence cue, the "why did you say this?" trace expansion, and the
 * `onSourceClick` override. Structure + behavior only (no styling).
 */

const src = (over: Partial<TuringRagSource> = {}): TuringRagSource => ({
  sourceId: "doc-1",
  title: "Doc One",
  url: "https://example.com/1",
  chunkIndex: 0,
  score: 0.9,
  keywordOnly: false,
  ...over,
});

describe("TuringSourceChips", () => {
  it("renders nothing when there are no sources", () => {
    const { container } = render(<TuringSourceChips sources={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it("renders one chip per distinct source and groups chunks", () => {
    render(
      <TuringSourceChips
        sources={[
          src({ sourceId: "a", title: "Alpha", chunkIndex: 0 }),
          src({ sourceId: "a", title: "Alpha", chunkIndex: 3 }),
          src({ sourceId: "b", title: "Beta", chunkIndex: 1 }),
        ]}
      />,
    );
    expect(screen.getByText("Alpha")).toBeTruthy();
    expect(screen.getByText("Beta")).toBeTruthy();
    // Two source chips → two expandable buttons.
    expect(screen.getAllByRole("button")).toHaveLength(2);
  });

  it("expands the trace panel on click and shows the cited passages", () => {
    render(<TuringSourceChips sources={[src({ chunkIndex: 2, score: 0.81 })]} />);
    // Collapsed: no region yet.
    expect(screen.queryByRole("region")).toBeNull();
    fireEvent.click(screen.getByRole("button"));
    expect(screen.getByRole("region")).toBeTruthy();
    expect(screen.getByText(/Why did you say this\?/)).toBeTruthy();
    expect(screen.getByText(/Passage #2/)).toBeTruthy();
  });

  it("marks the confidence tier via data-confidence", () => {
    const { container } = render(
      <TuringSourceChips sources={[src({ score: 0.95 })]} />,
    );
    const chip = container.querySelector("button[data-confidence]");
    expect(chip?.getAttribute("data-confidence")).toBe("high");
  });

  it("treats a keyword-only source as low confidence", () => {
    const { container } = render(
      <TuringSourceChips sources={[src({ score: 0.99, keywordOnly: true })]} />,
    );
    const chip = container.querySelector("button[data-confidence]");
    expect(chip?.getAttribute("data-confidence")).toBe("low");
    expect(chip?.hasAttribute("data-keyword-only")).toBe(true);
  });

  it("invokes onSourceClick and suppresses default navigation", () => {
    const onSourceClick = vi.fn();
    render(<TuringSourceChips sources={[src()]} onSourceClick={onSourceClick} />);
    fireEvent.click(screen.getByRole("button")); // expand
    const link = screen.getByText("Open source");
    fireEvent.click(link);
    expect(onSourceClick).toHaveBeenCalledTimes(1);
    expect(onSourceClick.mock.calls[0][0].sourceId).toBe("doc-1");
  });
});
