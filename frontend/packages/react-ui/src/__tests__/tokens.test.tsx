import { readFileSync } from "node:fs";
import { join } from "node:path";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
  TURING_UI_THEME_CLASS,
  TURING_UI_TOKENS,
} from "../tokens";
import { TuringChatMessage } from "../TuringChatMessage";
import { TuringCopyButton } from "../TuringCopyButton";
import { TuringCodeBlock } from "../TuringCodeBlock";
import { TuringThinkingDots } from "../TuringThinkingDots";

/**
 * Token-theming contract (T305). The optional stylesheet drives every component
 * through its `--turing-ui-*` tokens and its `data-turing-*` attributes — these
 * tests pin both halves of that contract so they can't silently drift:
 *   1. the TS token map matches the CSS token defaults, and the CSS scopes under
 *      the documented `.turing-ui-theme` class;
 *   2. the components still emit the `data-turing-*` hooks the CSS selects on.
 */

// vitest runs from the package root, so resolve the source stylesheet from cwd.
const css = readFileSync(
  join(process.cwd(), "src/styles/turing-ui.css"),
  "utf8",
);

describe("token theming — stylesheet contract", () => {
  it("scopes its rules under the documented theme class", () => {
    expect(TURING_UI_THEME_CLASS).toBe("turing-ui-theme");
    expect(css).toContain(`.${TURING_UI_THEME_CLASS}`);
  });

  it("declares a default for every token in the TS map (no drift)", () => {
    for (const token of Object.values(TURING_UI_TOKENS)) {
      // each token must both be DECLARED (`--x:`) and CONSUMED (`var(--x`)
      expect(css, `${token} default declaration`).toContain(`${token}:`);
      expect(css, `${token} usage`).toContain(`var(${token}`);
    }
  });

  it("targets each component's data-turing-* theming hook", () => {
    for (const hook of [
      "[data-turing-html-sandbox]",
      "[data-turing-fullscreen]",
      "[data-turing-code-block]",
      "[data-turing-copy-button]",
      "[data-turing-thinking-dots]",
      "[data-turing-d2]",
      "[data-turing-chat-message]",
    ]) {
      expect(css, hook).toContain(hook);
    }
  });

  it("ships the dot keyframe and never resets the inline per-dot delay", () => {
    expect(css).toContain("@keyframes turing-ui-dot-bounce");
    // must use animation longhands, not the `animation` shorthand, or the
    // component's inline `animation-delay` stagger would be reset to 0.
    expect(css).toContain("animation-name: turing-ui-dot-bounce");
    expect(css).not.toMatch(/animation:\s*turing-ui-dot-bounce/);
  });
});

describe("token theming — component hooks the CSS relies on", () => {
  it("TuringChatMessage tags the row by role", () => {
    const { container } = render(
      <TuringChatMessage role="assistant">hi</TuringChatMessage>,
    );
    const root = container.querySelector<HTMLElement>(
      "[data-turing-chat-message]",
    );
    expect(root).not.toBeNull();
    expect(root?.dataset.role).toBe("assistant");
  });

  it("TuringCopyButton reflects its state for the [data-turing-copy-button] selector", () => {
    const { container } = render(<TuringCopyButton value="x" />);
    expect(
      container.querySelector('[data-turing-copy-button="idle"]'),
    ).not.toBeNull();
  });

  it("TuringThinkingDots exposes its dots as direct span children", () => {
    const { container } = render(<TuringThinkingDots count={3} />);
    const dots = container.querySelectorAll(
      "[data-turing-thinking-dots] > span",
    );
    expect(dots).toHaveLength(3);
    // the stagger is inline so the shorthand-free keyframe rule can't reset it
    expect((dots[1] as HTMLElement).style.animationDelay).not.toBe("");
  });

  it("TuringCodeBlock emits its theming hook", () => {
    const { container } = render(
      <TuringCodeBlock code="const x = 1;" language="ts" />,
    );
    expect(container.querySelector("[data-turing-code-block]")).not.toBeNull();
  });
});
