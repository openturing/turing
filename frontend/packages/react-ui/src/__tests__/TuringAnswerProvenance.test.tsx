import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { TuringAnswerProvenance } from "../TuringAnswerProvenance";

/**
 * T839 — headless-contract tests for the "why this answer" provenance panel.
 */
describe("TuringAnswerProvenance (T839)", () => {
  it("surfaces KB mode, rerank strategy, source count and per-source rank breakdown", () => {
    render(
      <TuringAnswerProvenance
        defaultOpen
        trace={{ sourceCount: 3, answerLength: 120, kbMode: "HYBRID", rerankStrategy: "COHERE", rerankEnabled: true }}
        citations={[
          { rank: 1, title: "Model A", rankingExplanation: { lexicalRank: 2, semanticRank: 1, rrfScore: 0.031 } },
        ]}
      />,
    );

    expect(screen.getByText("Why this answer")).toBeInTheDocument();
    expect(screen.getByText("HYBRID")).toBeInTheDocument();
    expect(screen.getByText("COHERE")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    // Per-source explanation entries render as label/value pairs.
    expect(screen.getByText("lexicalRank")).toBeInTheDocument();
    expect(screen.getByText("0.031")).toBeInTheDocument();
    expect(screen.getByText(/\[1\] Model A/)).toBeInTheDocument();
  });

  it("shows rerank as off when disabled", () => {
    render(<TuringAnswerProvenance defaultOpen trace={{ kbMode: "VECTOR", rerankEnabled: false }} />);

    expect(screen.getByText("off")).toBeInTheDocument();
    expect(screen.getByText("VECTOR")).toBeInTheDocument();
  });

  it("renders nothing when there is no trace and no explanations", () => {
    const { container } = render(<TuringAnswerProvenance citations={[{ rank: 1, title: "A" }]} />);

    expect(container.querySelector("[data-turing-answer-provenance]")).toBeNull();
  });
});
