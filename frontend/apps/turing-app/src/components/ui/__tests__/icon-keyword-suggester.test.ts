import { describe, expect, it } from "vitest";

import { parseKeywords } from "../icon-keyword-suggester";

/**
 * T1009 — reading the keyword list out of a model reply.
 *
 * This is the half of the icon picker's suggestion pass that stayed in the
 * product when the dialog moved to the design system, and it is the half that
 * can actually go wrong: the repository's standing note is not to rely on a
 * small model honouring a structured-output instruction, so the parse has to
 * survive prose, a code fence and a truncated reply without throwing — a throw
 * here would reach the user as a dead suggest button.
 */
describe("parseKeywords", () => {
  it("reads a bare JSON array", () => {
    expect(parseKeywords('["briefcase","calendar","target"]')).toEqual([
      "briefcase",
      "calendar",
      "target",
    ]);
  });

  it("finds the array inside surrounding prose", () => {
    const reply = 'Sure! Here are five keywords:\n["rocket","chart","target"]\nHope that helps.';
    expect(parseKeywords(reply)).toEqual(["rocket", "chart", "target"]);
  });

  it("finds the array inside a fenced block", () => {
    const reply = '```json\n["gear", "wrench"]\n```';
    expect(parseKeywords(reply)).toEqual(["gear", "wrench"]);
  });

  it("caps the list, so a chatty model cannot spray the dialog", () => {
    const reply = JSON.stringify(["a", "b", "c", "d", "e", "f", "g", "h"]);
    expect(parseKeywords(reply)).toHaveLength(5);
  });

  it("drops blanks and non-strings rather than searching for them", () => {
    const reply = '["briefcase", "", "   ", 42, null, "calendar"]';
    expect(parseKeywords(reply)).toEqual(["briefcase", "calendar"]);
  });

  it("trims, because a keyword with padding is a different Iconify query", () => {
    expect(parseKeywords('["  rocket  ","chart\\n"]')).toEqual(["rocket", "chart"]);
  });

  it("returns nothing for a reply with no array at all", () => {
    expect(parseKeywords("I'm sorry, I can't help with that.")).toEqual([]);
  });

  it("returns nothing for a truncated array instead of throwing", () => {
    // The failure mode that matters: a cut-off reply must leave the dialog on
    // its empty state, not propagate a SyntaxError out of the suggest handler.
    expect(parseKeywords('["briefcase","cal')).toEqual([]);
  });

  it("returns nothing when the bracketed span is not an array of anything usable", () => {
    expect(parseKeywords("[]")).toEqual([]);
    expect(parseKeywords('[{"icon":"rocket"}]')).toEqual([]);
  });
});
