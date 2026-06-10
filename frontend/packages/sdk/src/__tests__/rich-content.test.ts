import { describe, expect, it } from "vitest";
import { hasHtmlBlock, splitRichContent } from "../rich-content";

describe("splitRichContent", () => {
  it("returns a single markdown segment when there is no html block", () => {
    expect(splitRichContent("just **text**")).toEqual([
      { type: "markdown", text: "just **text**" },
    ]);
  });

  it("splits prose around an html block", () => {
    const md = "Aqui está o Pong:\n\n```html\n<canvas></canvas>\n<script>1</script>\n```\n\nPronto!";
    const segs = splitRichContent(md);
    expect(segs).toHaveLength(3);
    expect(segs[0]).toEqual({ type: "markdown", text: "Aqui está o Pong:\n\n" });
    expect(segs[1]).toEqual({ type: "html", code: "<canvas></canvas>\n<script>1</script>" });
    expect(segs[2]).toEqual({ type: "markdown", text: "\n\nPronto!" });
  });

  it("handles a reply that is only an html block", () => {
    const segs = splitRichContent("```html\n<b>hi</b>\n```");
    expect(segs).toEqual([{ type: "html", code: "<b>hi</b>" }]);
  });

  it("captures multiple html blocks", () => {
    const segs = splitRichContent("```html\n<a/>\n```\nmid\n```html\n<b/>\n```");
    expect(segs.filter((s) => s.type === "html")).toHaveLength(2);
    expect(segs.find((s) => s.type === "markdown")).toEqual({
      type: "markdown",
      text: "\nmid\n",
    });
  });

  it("splits a ```d2 diagram block out of prose", () => {
    const md = "Here is the flow:\n\n```d2\na -> b\n```\n\nDone.";
    const segs = splitRichContent(md);
    expect(segs).toEqual([
      { type: "markdown", text: "Here is the flow:\n\n" },
      { type: "d2", code: "a -> b" },
      { type: "markdown", text: "\n\nDone." },
    ]);
  });

  it("interleaves html and d2 blocks in order", () => {
    const segs = splitRichContent("```html\n<b/>\n```\nx\n```d2\nc -> d\n```");
    expect(segs.map((s) => s.type)).toEqual(["html", "markdown", "d2"]);
  });

  it("returns [] for empty input", () => {
    expect(splitRichContent("")).toEqual([]);
  });
});

describe("hasHtmlBlock", () => {
  it("detects a renderable html block", () => {
    expect(hasHtmlBlock("x\n```html\n<canvas></canvas>\n```")).toBe(true);
  });
  it("is false for prose or a non-html fence", () => {
    expect(hasHtmlBlock("```js\nconst a=1\n```")).toBe(false);
    expect(hasHtmlBlock("plain")).toBe(false);
  });
});
