import { describe, expect, it } from "vitest";

import {
  detectOverlaps,
  formatItemSummary,
  isDateMath,
  normalizeItems,
  parseIsoDateValue,
} from "../facet/sn.site.custom.facet.form.utils";

/**
 * T1075 — a custom facet item over a DATE field can hold a relative bound
 * (`NOW-7DAYS`) that the server stores as typed, instead of a fixed date.
 */
describe("relative date bounds on custom facet items", () => {
  it("recognises date math by its NOW anchor", () => {
    expect(isDateMath("NOW-7DAYS")).toBe(true);
    expect(isDateMath(" now/day")).toBe(true);
    expect(isDateMath("2026-10-01")).toBe(false);
    expect(isDateMath(null)).toBe(false);
  });

  it("keeps date math as typed (upper-cased) and fixed dates as ISO instants", () => {
    expect(parseIsoDateValue("now-7days")).toBe("NOW-7DAYS");
    expect(parseIsoDateValue("2026-10-01T00:00:00Z")).toBe("2026-10-01T00:00:00.000Z");
    expect(parseIsoDateValue("not a date")).toBeNull();
  });

  it("survives normalisation before save", () => {
    const [item] = normalizeItems(
      [{ label: "Last 7 days", operator: "GREATER_THAN_OR_EQUAL", rangeStartDate: "NOW-7DAYS" }],
      "DATE",
    );
    expect(item.rangeStartDate).toBe("NOW-7DAYS");
  });

  it("shows the expression in the summary, not an invalid date", () => {
    expect(
      formatItemSummary(
        { label: "Last 7 days", operator: "GREATER_THAN_OR_EQUAL", rangeStartDate: "NOW-7DAYS" },
        true,
        "publishedAt",
      ),
    ).toBe("publishedAt ≥ NOW-7DAYS");
  });

  it("leaves relative buckets out of the overlap check, since they move with today", () => {
    const items = [
      { label: "Last day", operator: "GREATER_THAN_OR_EQUAL" as const, rangeStartDate: "NOW-1DAY" },
      { label: "Last week", operator: "GREATER_THAN_OR_EQUAL" as const, rangeStartDate: "NOW-7DAYS" },
    ];
    expect(detectOverlaps(items, true)).toEqual([]);
  });
});
