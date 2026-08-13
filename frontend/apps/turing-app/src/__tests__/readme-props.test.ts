import { describe, expect, it } from "vitest";

// @ts-expect-error — a zero-dependency .mjs rule set shared with the workspace
// scripts suite; no types by design, same as search-contract-rules.mjs.
import { propsIndex, violations } from "../../../../scripts/readme-prop-rules.mjs";

/**
 * T944 / §LXIX.29 — a README fence hands a reader a prop the component has.
 *
 * <p>T939 runs the search-contract rules over the same fences, which is one rule
 * out of everything a fence can get wrong. The first thing it did not catch sat
 * two lines above a fence it did: the `TuringResultList` example passed
 * `renderItem`, and the component's one required render prop is
 * `itemComponent`. The documented usage neither compiled nor rendered, and had
 * not since the rename — a fence is prose to every tool in the repo, so a rename
 * a build catches anywhere else passed here in silence.
 *
 * <p>§LXIX.29 proposed compiling the fences with `tsc`. Measured first: of the
 * workspace's 41 README fences, 18 are `tsx` and 7 reference a Turing component,
 * and almost none is a compilable program — they are snippets whose `turing`,
 * `pages` and `store` arrive from the paragraph above. Compiling would have
 * meant marking most of them partial, and a guard most of its subjects opt out
 * of is a guard in name only. The props are checked instead: no fence has to be
 * self-contained, and the class this task is about — a prop that outlived its
 * rename — is caught exactly.
 *
 * <p>This file is the app's entry point into the rule, mirroring how
 * `search-store-boundary.test.ts` is T939's, so
 * `pnpm --filter turing-app test` fails on a violation without anyone
 * remembering to run the scripts suite.
 */
describe("README component props (T944)", () => {
  it("no fence passes a prop its component does not declare", () => {
    expect(violations()).toEqual([]);
  });

  /**
   * A guard whose index is empty passes everything. Renaming a props interface
   * must fail here rather than quietly disarm the check.
   */
  it("derives the props from the workspace's own sources", () => {
    const index = propsIndex() as Map<string, Set<string>>;
    expect(index.size).toBeGreaterThan(10);

    const resultList = index.get("TuringResultList");
    expect(resultList).toBeDefined();
    expect(resultList?.has("itemComponent")).toBe(true);
    expect(resultList?.has("renderItem")).toBe(false);
  });
});
