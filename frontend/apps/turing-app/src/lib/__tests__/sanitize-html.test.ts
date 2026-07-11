import { describe, expect, it } from "vitest";
import { sanitizeFragment, sanitizeHighlight } from "../sanitize-html";

// T653 / §XXXVII.15 — client-side XSS sanitisers for the search-highlight and
// Gemini-chip dangerouslySetInnerHTML sinks.
describe("sanitizeHighlight", () => {
  it("keeps allowlisted inline highlight tags", () => {
    expect(sanitizeHighlight("a <mark>hit</mark> b")).toBe("a <mark>hit</mark> b");
    expect(sanitizeHighlight("<em>x</em> <strong>y</strong>")).toBe(
      "<em>x</em> <strong>y</strong>",
    );
  });

  it("escapes script and other markup", () => {
    expect(sanitizeHighlight("<script>alert(1)</script>")).toBe(
      "&lt;script&gt;alert(1)&lt;/script&gt;",
    );
    expect(sanitizeHighlight('<img src=x onerror="alert(1)">')).toBe(
      "&lt;img src=x onerror=&quot;alert(1)&quot;&gt;",
    );
  });

  it("strips attributes from otherwise-allowed tags", () => {
    // <mark onmouseover=...> must NOT survive as a live tag — the attribute form
    // is escaped because only the bare <mark> pattern is re-allowed.
    const out = sanitizeHighlight('<mark onmouseover="alert(1)">x</mark>');
    expect(out).toContain("&lt;mark onmouseover");
    expect(out).toContain("</mark>");
    expect(out).not.toContain("<mark onmouseover");
  });

  it("handles null/undefined/empty", () => {
    expect(sanitizeHighlight(null)).toBe("");
    expect(sanitizeHighlight(undefined)).toBe("");
    expect(sanitizeHighlight("")).toBe("");
  });
});

describe("sanitizeFragment", () => {
  it("removes script and iframe elements", () => {
    expect(sanitizeFragment("<div>ok</div><script>evil()</script>")).toBe("<div>ok</div>");
    expect(sanitizeFragment('<iframe src="//evil"></iframe><span>x</span>')).toBe("<span>x</span>");
  });

  it("strips inline event handlers", () => {
    expect(sanitizeFragment('<a href="/x" onclick="evil()">go</a>')).not.toContain("onclick");
  });

  it("neutralises javascript: URLs", () => {
    expect(sanitizeFragment('<a href="javascript:evil()">x</a>')).not.toContain("javascript:");
  });

  it("preserves benign chip markup + inline styles", () => {
    const chip = '<div style="color:red"><a href="https://google.com/search?q=x">q</a></div>';
    expect(sanitizeFragment(chip)).toBe(chip);
  });
});
