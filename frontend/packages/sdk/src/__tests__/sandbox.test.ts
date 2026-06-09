import { describe, expect, it } from "vitest";
import { absolutizeArtifactUrls, isSandboxUrl, resolveSandboxUrl } from "../sandbox";

describe("resolveSandboxUrl", () => {
  it("strips the sandbox: scheme to a relative path (no base)", () => {
    expect(resolveSandboxUrl("sandbox:/api/v2/code-interpreter/s1/c.png")).toBe(
      "/api/v2/code-interpreter/s1/c.png",
    );
  });

  it("absolutizes a sandbox: URL against a base", () => {
    expect(
      resolveSandboxUrl("sandbox:/api/v2/code-interpreter/s1/c.png", "https://t.example.com"),
    ).toBe("https://t.example.com/api/v2/code-interpreter/s1/c.png");
  });

  it("absolutizes a bare relative /api path against a base", () => {
    expect(resolveSandboxUrl("/api/v2/x.png", "https://t.example.com/")).toBe(
      "https://t.example.com/api/v2/x.png",
    );
  });

  it("collapses doubled scheme and extra slashes", () => {
    expect(resolveSandboxUrl("sandbox:sandbox:/api/x.png")).toBe("/api/x.png");
    expect(resolveSandboxUrl("sandbox://api/x.png")).toBe("/api/x.png");
  });

  it("leaves absolute and non-path URLs untouched", () => {
    expect(resolveSandboxUrl("https://h/x.png", "https://t")).toBe("https://h/x.png");
    expect(resolveSandboxUrl("mailto:a@b.com")).toBe("mailto:a@b.com");
  });
});

describe("isSandboxUrl", () => {
  it("detects the scheme case-insensitively", () => {
    expect(isSandboxUrl("sandbox:/api/x")).toBe(true);
    expect(isSandboxUrl("SANDBOX:/api/x")).toBe(true);
    expect(isSandboxUrl("/api/x")).toBe(false);
  });
});

describe("absolutizeArtifactUrls", () => {
  const base = "https://turing.example.com";

  it("rewrites a relative artifact image to absolute (the viglet-com BFF case)", () => {
    const md = "Aqui:\n\n![Chart](/api/v2/code-interpreter/d2e6c90c/bar_chart_example.png)\n";
    expect(absolutizeArtifactUrls(md, base)).toContain(
      "![Chart](https://turing.example.com/api/v2/code-interpreter/d2e6c90c/bar_chart_example.png)",
    );
  });

  it("rewrites a sandbox:-prefixed artifact link too", () => {
    expect(
      absolutizeArtifactUrls("[Download r.pdf](sandbox:/api/v2/code-interpreter/s1/r.pdf?sig=abc)", base),
    ).toBe(`[Download r.pdf](${base}/api/v2/code-interpreter/s1/r.pdf?sig=abc)`);
  });

  it("preserves the signed query string", () => {
    expect(absolutizeArtifactUrls("![x](/api/v2/x.png?sig=a&exp=9)", base)).toBe(
      `![x](${base}/api/v2/x.png?sig=a&exp=9)`,
    );
  });

  it("rewrites every artifact link in the blob", () => {
    const md = "![a](/api/v2/a.png) e ![b](/api/v2/b.png)";
    expect(absolutizeArtifactUrls(md, base)).toBe(
      `![a](${base}/api/v2/a.png) e ![b](${base}/api/v2/b.png)`,
    );
  });

  it("does not touch already-absolute artifact URLs", () => {
    const md = `![x](${base}/api/v2/x.png)`;
    expect(absolutizeArtifactUrls(md, base)).toBe(md);
  });

  it("does not touch non-/api links", () => {
    const md = "[home](/about) and [docs](https://docs.example.com)";
    expect(absolutizeArtifactUrls(md, base)).toBe(md);
  });

  it("is a no-op when base is empty", () => {
    const md = "![x](/api/v2/x.png)";
    expect(absolutizeArtifactUrls(md, "")).toBe(md);
  });

  it("uses the base ORIGIN so a base ending in /api does not double the segment", () => {
    // Regression: TURING_API_URL is often http://host:2700/api; the artifact
    // path is already domain-rooted (/api/v2/…), so naive base+path produced
    // /api/api/v2/… (404). Resolve against the origin instead.
    expect(
      absolutizeArtifactUrls(
        "![x](/api/v2/code-interpreter/s1/c.png)",
        "http://localhost:2700/api",
      ),
    ).toBe("![x](http://localhost:2700/api/v2/code-interpreter/s1/c.png)");
  });
});

describe("resolveSandboxUrl with a path-bearing base", () => {
  it("does not double /api when the base carries the /api path", () => {
    expect(resolveSandboxUrl("/api/v2/x.png", "http://localhost:2700/api")).toBe(
      "http://localhost:2700/api/v2/x.png",
    );
  });
});
