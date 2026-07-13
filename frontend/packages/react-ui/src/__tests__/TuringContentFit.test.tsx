import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { TuringContentFit, type TuringContentFitResult } from "../TuringContentFit";

function result(over: Partial<TuringContentFitResult> = {}): TuringContentFitResult {
  return {
    personaId: "p1",
    personaName: "Skeptical Developer",
    fitScore: 66,
    summary: "Mostly fits, some jargon.",
    fits: ["Concrete examples"],
    misfits: [{ span: "leverage synergies", reason: "jargon", suggestion: "work together" }],
    llmUsed: true,
    ...over,
  };
}

describe("TuringContentFit (T636)", () => {
  it("renders the persona name, fit %, summary, fits and misfits", () => {
    render(<TuringContentFit result={result()} />);
    expect(screen.getByText("Skeptical Developer")).toBeInTheDocument();
    expect(screen.getByText("66%")).toBeInTheDocument();
    expect(screen.getByText("Mostly fits, some jargon.")).toBeInTheDocument();
    expect(screen.getByText("Concrete examples")).toBeInTheDocument();
    expect(screen.getByText("leverage synergies")).toBeInTheDocument();
    expect(screen.getByText("work together")).toBeInTheDocument();
  });

  it("exposes the fit as an accessible progressbar clamped to 0..100", () => {
    render(<TuringContentFit result={result({ fitScore: 140 })} />);
    const bar = screen.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "100");
    expect(screen.getByText("100%")).toBeInTheDocument();
  });

  it("tags each misfit with its reason", () => {
    const { container } = render(<TuringContentFit result={result()} />);
    expect(container.querySelector('[data-reason="jargon"]')).not.toBeNull();
  });

  it("shows the fallback note only when the LLM was not used", () => {
    const { rerender } = render(<TuringContentFit result={result({ llmUsed: true })} />);
    expect(screen.queryByText(/Readability score only/i)).toBeNull();
    rerender(<TuringContentFit result={result({ llmUsed: false })} />);
    expect(screen.getByText(/Readability score only/i)).toBeInTheDocument();
  });
});
