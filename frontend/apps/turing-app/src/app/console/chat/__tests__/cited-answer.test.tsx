import { render, screen } from "@testing-library/react";
import { TuringCitedAnswer, segmentCitedAnswer, type TuringCitation } from "@viglet/turing-react-sdk";
import { describe, expect, it } from "vitest";

/**
 * T154 / §X.7.c — citation-aware answer rendering. Covers the pure segmentation
 * logic and the headless component's default underline output.
 */
describe("segmentCitedAnswer", () => {
  const cite = (over: Partial<TuringCitation>): TuringCitation => ({
    documentIndex: 0,
    sourceId: "doc-a",
    documentTitle: "Doc A",
    url: "/sn/site/document/doc-a",
    citedText: "source quote",
    locationType: "search_result",
    answerStart: 0,
    answerEnd: 0,
    ...over,
  });

  it("splits the answer into uncited + cited runs at span boundaries", () => {
    const text = "Intro text. The sky is blue.";
    const segments = segmentCitedAnswer(text, [
      cite({ answerStart: "Intro text. ".length, answerEnd: text.length }),
    ]);
    expect(segments).toHaveLength(2);
    expect(segments[0]).toEqual({ text: "Intro text. ", citations: [] });
    expect(segments[1].text).toBe("The sky is blue.");
    expect(segments[1].citations).toHaveLength(1);
  });

  it("groups multiple citations that share the same span", () => {
    const text = "Grounded claim.";
    const segments = segmentCitedAnswer(text, [
      cite({ sourceId: "doc-a", answerStart: 0, answerEnd: text.length }),
      cite({ sourceId: "doc-b", answerStart: 0, answerEnd: text.length }),
    ]);
    expect(segments).toHaveLength(1);
    expect(segments[0].citations).toHaveLength(2);
  });

  it("drops citations with out-of-range or inverted offsets, keeping all text", () => {
    const text = "Plain answer.";
    const segments = segmentCitedAnswer(text, [
      cite({ answerStart: 5, answerEnd: 2 }), // inverted
      cite({ answerStart: 0, answerEnd: 999 }), // out of range
    ]);
    expect(segments).toEqual([{ text, citations: [] }]);
  });

  it("returns nothing for empty text", () => {
    expect(segmentCitedAnswer("", [])).toEqual([]);
  });
});

describe("TuringCitedAnswer", () => {
  it("wraps the cited span in a mark and exposes the source quote in the title", () => {
    const text = "Intro. The sky is blue.";
    render(
      <TuringCitedAnswer
        text={text}
        citations={[
          {
            documentIndex: 0,
            sourceId: "doc-a",
            documentTitle: "Doc A",
            url: "/u",
            citedText: "The sky is blue today.",
            locationType: "search_result",
            answerStart: "Intro. ".length,
            answerEnd: text.length,
          },
        ]}
      />,
    );
    const mark = document.querySelector("mark[data-turing-citation]");
    expect(mark).not.toBeNull();
    expect(mark?.textContent).toBe("The sky is blue.");
    expect(mark?.getAttribute("title")).toContain("The sky is blue today.");
    // Uncited prefix still rendered.
    expect(screen.getByText("Intro.", { exact: false })).toBeTruthy();
  });
});
