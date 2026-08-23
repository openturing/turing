import { describe, expect, it } from "vitest";

import { BENTO_TONES, BENTO_TONE_CLASS, bentoChipClass } from "../bento-tones";

/**
 * T996 — the tone is a token, and this product names no hue.
 *
 * The palette itself is the design system's and is covered there. What is still
 * this product's to prove is the contract its two heroes now depend on: that
 * `@/components/bento` resolves to the package's tone module, and that
 * `bentoChipClass` emits the chip class plus a tone class rather than a Tailwind
 * colour pair. A hero rendering `bento-chip bento-tone-blue` takes its gradient
 * from `--bento-tone-from/-to`; a hero rendering `from-blue-600` does not, and
 * that is the regression this replaces.
 *
 * jsdom computes no CSS, so the *colours* cannot be asserted here. They were
 * verified by reading: all fourteen preset values are exactly the Tailwind
 * colours the deleted `BENTO_TONE_GRADIENTS` named, tone for tone. That check
 * belongs to the package, which owns both halves of it now.
 */
describe("the product's tone module", () => {
  it("is the design system's, not a local palette", async () => {
    const pkg = await import("@viglet/viglet-design-system/bento");
    expect(bentoChipClass).toBe(pkg.bentoChipClass);
    expect(BENTO_TONE_CLASS).toBe(pkg.BENTO_TONE_CLASS);
  });

  it("emits the chip class plus a tone class, never a colour", () => {
    for (const tone of BENTO_TONES) {
      const classes = bentoChipClass(tone);
      expect(classes).toContain("bento-chip");
      expect(classes).toContain(`bento-tone-${tone}`);
      // The whole point: no hue survives in the class list a page renders.
      expect(classes).not.toMatch(/\bfrom-[a-z]+-\d{3}\b/);
      expect(classes).not.toMatch(/\bto-[a-z]+-\d{3}\b/);
    }
  });

  it("still offers the seven tones the deleted gradient map named", () => {
    // The map this replaced had exactly these keys. A tone quietly disappearing
    // would leave a page passing a value that falls back to slate.
    expect([...BENTO_TONES].sort()).toEqual(
      ["amber", "blue", "emerald", "indigo", "rose", "slate", "violet"],
    );
  });

  it("falls back to slate for an unknown tone, so a bad value looks plain", () => {
    // Not unstyled: a page reading a tone off persisted data can hand over
    // anything, and an unrecognised one should still paint a chip.
    const classes = bentoChipClass(undefined);
    expect(classes).toContain("bento-chip");
    expect(classes).toContain("bento-tone-slate");
  });
});
