import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { rehypeCitationAnchors } from "../rehypeCitationAnchors";
import {
  TuringCitationAnchor,
  citationMarkdownComponents,
} from "../TuringCitationAnchor";
import { TuringMarkdown } from "../TuringMarkdown";

/**
 * T836 — headless-contract tests for the inline `[n]` citation anchors: the
 * rehype transform, the anchor's scroll+highlight behavior, and the end-to-end
 * render through TuringMarkdown.
 */

interface HastNode {
  type: string;
  tagName?: string;
  value?: string;
  properties?: Record<string, unknown>;
  children?: HastNode[];
}

function paragraph(text: string): HastNode {
  return {
    type: "root",
    children: [
      { type: "element", tagName: "p", properties: {}, children: [{ type: "text", value: text }] },
    ],
  };
}

function childrenOfParagraph(tree: HastNode): HastNode[] {
  return tree.children?.[0]?.children ?? [];
}

describe("rehypeCitationAnchors", () => {
  it("rewrites [n] markers into sup anchor elements, keeping surrounding text", () => {
    const tree = paragraph("See [1] and [2] here");
    rehypeCitationAnchors()(tree);

    const kids = childrenOfParagraph(tree);
    expect(kids.map((k) => k.tagName ?? k.type)).toEqual([
      "text",
      "sup",
      "text",
      "sup",
      "text",
    ]);
    expect(kids[1].properties?.dataTuringCitation).toBe("1");
    expect(kids[3].properties?.dataTuringCitation).toBe("2");
    expect(kids[0].value).toBe("See ");
    expect(kids[4].value).toBe(" here");
  });

  it("leaves markers inside code untouched", () => {
    const tree: HastNode = {
      type: "root",
      children: [
        { type: "element", tagName: "code", properties: {}, children: [{ type: "text", value: "arr[0]" }] },
      ],
    };
    rehypeCitationAnchors()(tree);

    const codeKids = tree.children?.[0]?.children ?? [];
    expect(codeKids).toHaveLength(1);
    expect(codeKids[0]).toEqual({ type: "text", value: "arr[0]" });
  });

  it("respects isValidCitation, leaving unbacked markers as plain text", () => {
    const tree = paragraph("a [1] b [9] c");
    rehypeCitationAnchors({ isValidCitation: (n) => n === 1 })(tree);

    const kids = childrenOfParagraph(tree);
    const sups = kids.filter((k) => k.tagName === "sup");
    expect(sups).toHaveLength(1);
    expect(sups[0].properties?.dataTuringCitation).toBe("1");
    // The filtered [9] survives as plain text.
    expect(kids.map((k) => k.value).join("")).toContain("[9]");
  });
});

describe("TuringCitationAnchor", () => {
  it("scroll-links to and highlights the matching source card on click", () => {
    document.body.innerHTML = '<div id="turing-source-3">card</div>';
    const scrollIntoView = vi.fn();
    Element.prototype.scrollIntoView = scrollIntoView;
    const onActivate = vi.fn();

    render(<TuringCitationAnchor index={3} citedText="the grounded passage" onActivate={onActivate} />);
    const button = screen.getByRole("button", { name: /citation 3/i });
    expect(button).toHaveAttribute("title", "the grounded passage");

    fireEvent.click(button);

    expect(scrollIntoView).toHaveBeenCalledTimes(1);
    expect(document.getElementById("turing-source-3")).toHaveAttribute("data-turing-citation-target");
    expect(onActivate).toHaveBeenCalledWith(3);
  });

  it("honours a custom idPrefix", () => {
    document.body.innerHTML = '<div id="result-2">row</div>';
    Element.prototype.scrollIntoView = vi.fn();

    render(<TuringCitationAnchor index={2} idPrefix="result-" />);
    fireEvent.click(screen.getByRole("button", { name: /citation 2/i }));

    expect(document.getElementById("result-2")).toHaveAttribute("data-turing-citation-target");
  });
});

describe("citationMarkdownComponents", () => {
  it("renders a data-turing-citation sup as an anchor and passes plain sups through", () => {
    const components = citationMarkdownComponents({ citations: [{ index: 1, citedText: "quote" }] });
    const Sup = components.sup as (props: unknown) => JSX.Element;

    const { rerender } = render(Sup({ node: { properties: { dataTuringCitation: "1" } } }));
    expect(screen.getByRole("button", { name: /citation 1/i })).toBeInTheDocument();

    rerender(Sup({ node: { properties: {} }, children: "x²" }));
    // A genuine superscript stays a plain <sup> — no citation button.
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("renders both markers as anchors end-to-end through TuringMarkdown", () => {
    render(
      <TuringMarkdown
        rehypePlugins={[rehypeCitationAnchors]}
        components={citationMarkdownComponents({
          citations: [
            { index: 1, citedText: "first" },
            { index: 2, citedText: "second" },
          ],
        })}
      >
        {"See [1] and [2]."}
      </TuringMarkdown>,
    );

    expect(screen.getByRole("button", { name: /citation 1/i })).toHaveTextContent("[1]");
    expect(screen.getByRole("button", { name: /citation 2/i })).toHaveTextContent("[2]");
  });
});
