import { describe, expect, it } from "vitest";
import type { SearchStoreValue, TurStructuredSearchResponse } from "@viglet/turing-react-sdk";
import { toSearchView } from "../lib/search-view";

/**
 * T879 / §LXX.1 — the projection asserted without React.
 *
 * <p>`toSearchView` is exported as a pure function precisely so the two response
 * shapes can be compared field by field. The icon is the case that motivated it:
 * it is the one widget property that was never link-shaped, so it had no reason to
 * be dropped along with the hrefs, and its absence is invisible in review because
 * falling back to the Turing logo looks deliberate.
 */

function structuredStore(
  structured: Partial<TurStructuredSearchResponse>,
): SearchStoreValue {
  return {
    structured: {
      request: { q: "*", page: 1, rows: 10 },
      queryContext: { facetItemType: "OR" },
      ...structured,
    },
  } as unknown as SearchStoreValue;
}

describe("toSearchView", () => {
  it("carries the site icon off the structured response", () => {
    expect(toSearchView(structuredStore({ icon: "tabler:building-store" })).siteIcon).toBe(
      "tabler:building-store",
    );
  });

  it("leaves the icon null when the site configured none, so the logo shows", () => {
    expect(toSearchView(structuredStore({})).siteIcon).toBeNull();
  });

  it("still reads the icon off the legacy widget", () => {
    const store = {
      data: { widget: { icon: "tabler:book" }, queryContext: { facetItemType: "OR" } },
    } as unknown as SearchStoreValue;

    expect(toSearchView(store).siteIcon).toBe("tabler:book");
  });

  it("reports no icon before any response has arrived", () => {
    expect(toSearchView({} as unknown as SearchStoreValue).siteIcon).toBeNull();
  });
});
