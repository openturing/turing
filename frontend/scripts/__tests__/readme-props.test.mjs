/**
 * T944 / §LXIX.29 — the README fences pass props the components actually have.
 *
 * <p>Runs under `node --test` alongside the search-contract rules, so a
 * developer touching either sees the same failure the CI gate does.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.7
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { propsIndex, violations } from "../readme-prop-rules.mjs";

test("no README fence passes a prop its component does not declare", () => {
  assert.deepEqual(violations(), []);
});

test("the props index is derived from source, not empty", () => {
  // A guard whose index is empty passes everything. This is the assertion that
  // makes renaming an interface a failure rather than a silent disarm.
  const index = propsIndex();
  assert.ok(index.size > 10, `expected the workspace's components, got ${index.size}`);

  const resultList = index.get("TuringResultList");
  assert.ok(resultList, "TuringResultList must be indexed");
  assert.ok(resultList.has("itemComponent"), "the real render prop must be indexed");
  assert.ok(resultList.has("documents"));
  assert.ok(
    !resultList.has("renderItem"),
    "renderItem is the name T944 removed — indexing it would mean the interface still declares it",
  );
});

test("the index reads every member, including the optional and readonly ones", () => {
  const index = propsIndex();
  const resultList = index.get("TuringResultList");
  // `emptyComponent?`, `className?` and `onResultClick?` are optional; a member
  // parser that stopped at the first `?` or at a nested object type would miss
  // them, and then a typo in one would pass.
  for (const optional of ["emptyComponent", "loadingComponent", "isLoading", "className"]) {
    assert.ok(resultList.has(optional), `${optional} must be indexed`);
  }
  // TuringWorkspacePanelProps declares its members `readonly`.
  const panel = index.get("TuringWorkspacePanel");
  assert.ok(panel && panel.size > 0, "a readonly-member interface must still index");
});
