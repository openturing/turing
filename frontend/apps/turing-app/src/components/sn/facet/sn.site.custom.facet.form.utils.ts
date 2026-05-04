import type {
  TurSNSiteCustomFacetItem,
  TurSNSiteCustomFacetOperator,
} from "@/models/sn/sn-site-custom-facet.model";
import type { TurSNSiteFacetFieldTypes } from "@/models/sn/sn-site-facet.field.type";

export const FACET_NAME_PATTERN = /^[a-zA-Z0-9_-]+$/;
export const DATE_FIELD_TYPE = "DATE";
export const DEFAULT_OPERATOR: TurSNSiteCustomFacetOperator = "BETWEEN_INCLUSIVE";

export const facetTypes: { name: string; value: TurSNSiteFacetFieldTypes }[] = [
  { name: "Default", value: "DEFAULT" },
  { name: "And", value: "AND" },
  { name: "Or", value: "OR" },
];

export const OPERATOR_VALUES: TurSNSiteCustomFacetOperator[] = [
  "EQUAL",
  "GREATER_THAN",
  "GREATER_THAN_OR_EQUAL",
  "LESS_THAN",
  "LESS_THAN_OR_EQUAL",
  "BETWEEN_INCLUSIVE",
  "BETWEEN_EXCLUSIVE",
];

export const OPERATOR_TRANSLATION_KEYS: Record<
  TurSNSiteCustomFacetOperator,
  { label: string; description: string }
> = {
  EQUAL: { label: "forms.snCustomFacet.equal", description: "forms.snCustomFacet.equalDesc" },
  GREATER_THAN: { label: "forms.snCustomFacet.greaterThan", description: "forms.snCustomFacet.greaterThanDesc" },
  GREATER_THAN_OR_EQUAL: { label: "forms.snCustomFacet.greaterThanOrEqual", description: "forms.snCustomFacet.greaterThanOrEqualDesc" },
  LESS_THAN: { label: "forms.snCustomFacet.lessThan", description: "forms.snCustomFacet.lessThanDesc" },
  LESS_THAN_OR_EQUAL: { label: "forms.snCustomFacet.lessThanOrEqual", description: "forms.snCustomFacet.lessThanOrEqualDesc" },
  BETWEEN_INCLUSIVE: { label: "forms.snCustomFacet.betweenInclusive", description: "forms.snCustomFacet.betweenInclusiveDesc" },
  BETWEEN_EXCLUSIVE: { label: "forms.snCustomFacet.betweenExclusive", description: "forms.snCustomFacet.betweenExclusiveDesc" },
};

export function isBetweenOperator(operator?: TurSNSiteCustomFacetOperator): boolean {
  return operator === "BETWEEN_INCLUSIVE" || operator === "BETWEEN_EXCLUSIVE";
}

export function usesOnlyStartValue(operator?: TurSNSiteCustomFacetOperator): boolean {
  return (
    operator === "EQUAL" ||
    operator === "GREATER_THAN" ||
    operator === "GREATER_THAN_OR_EQUAL"
  );
}

export function usesOnlyEndValue(operator?: TurSNSiteCustomFacetOperator): boolean {
  return operator === "LESS_THAN" || operator === "LESS_THAN_OR_EQUAL";
}

export function isDateFieldType(fieldType?: string): boolean {
  return (fieldType ?? "").toUpperCase() === DATE_FIELD_TYPE;
}

export function parseRangeValue(value: unknown): number | null {
  if (value === null || value === undefined || value === "") return null;
  const numericValue = Number.parseFloat(String(value).replace(",", "."));
  return Number.isNaN(numericValue) ? null : numericValue;
}

export function parseIsoDateValue(value: unknown): string | null {
  if (value === null || value === undefined || value === "") return null;
  const parsedDate = new Date(String(value));
  return Number.isNaN(parsedDate.getTime()) ? null : parsedDate.toISOString();
}

export function toDateTimeLocalValue(value: string | null | undefined): string {
  if (!value) return "";
  const parsedDate = new Date(value);
  if (Number.isNaN(parsedDate.getTime())) return "";
  const timezoneOffsetMs = parsedDate.getTimezoneOffset() * 60 * 1000;
  return new Date(parsedDate.getTime() - timezoneOffsetMs).toISOString().slice(0, 16);
}

export function normalizeItems(
  itemsToNormalize: TurSNSiteCustomFacetItem[],
  fieldType?: string,
): TurSNSiteCustomFacetItem[] {
  const isDate = isDateFieldType(fieldType);
  return itemsToNormalize.map((item, index) => {
    const op = item.operator ?? DEFAULT_OPERATOR;
    let rangeStart = isDate ? null : parseRangeValue(item.rangeStart);
    let rangeEnd = isDate ? null : parseRangeValue(item.rangeEnd);
    let rangeStartDate = isDate ? parseIsoDateValue(item.rangeStartDate) : null;
    let rangeEndDate = isDate ? parseIsoDateValue(item.rangeEndDate) : null;

    if (usesOnlyEndValue(op)) {
      rangeStart = null;
      rangeStartDate = null;
    } else if (usesOnlyStartValue(op)) {
      rangeEnd = null;
      rangeEndDate = null;
    }

    return { ...item, position: index + 1, operator: op, rangeStart, rangeEnd, rangeStartDate, rangeEndDate };
  });
}

export function hasFilledRangeValues(itemsToCheck: TurSNSiteCustomFacetItem[]): boolean {
  return itemsToCheck.some((item) => {
    const hasNumberRange =
      (item.rangeStart !== null && item.rangeStart !== undefined) ||
      (item.rangeEnd !== null && item.rangeEnd !== undefined);
    const hasDateRange = Boolean(item.rangeStartDate) || Boolean(item.rangeEndDate);
    return hasNumberRange || hasDateRange;
  });
}

export function operatorLabel(
  t: (key: string) => string,
  operator?: TurSNSiteCustomFacetOperator,
): string {
  const op = operator ?? DEFAULT_OPERATOR;
  return t(OPERATOR_TRANSLATION_KEYS[op].label);
}

export function formatItemSummary(
  item: TurSNSiteCustomFacetItem,
  isDateField: boolean,
  variable: string,
): string {
  const op = item.operator ?? DEFAULT_OPERATOR;
  const startVal = isDateField
    ? (item.rangeStartDate ? new Date(item.rangeStartDate).toLocaleString() : "—")
    : (item.rangeStart ?? "—");
  const endVal = isDateField
    ? (item.rangeEndDate ? new Date(item.rangeEndDate).toLocaleString() : "—")
    : (item.rangeEnd ?? "—");

  switch (op) {
    case "EQUAL": return `${variable} = ${startVal}`;
    case "GREATER_THAN": return `${variable} > ${startVal}`;
    case "GREATER_THAN_OR_EQUAL": return `${variable} ≥ ${startVal}`;
    case "LESS_THAN": return `${variable} < ${endVal}`;
    case "LESS_THAN_OR_EQUAL": return `${variable} ≤ ${endVal}`;
    case "BETWEEN_EXCLUSIVE": return `${startVal} < ${variable} < ${endVal}`;
    case "BETWEEN_INCLUSIVE":
    default: return `${startVal} ≤ ${variable} ≤ ${endVal}`;
  }
}

/* ── Overlap detection ───────────────────────────────────────────────────── */

type Interval = {
  min: number | null;
  max: number | null;
  minInclusive: boolean;
  maxInclusive: boolean;
};

export type OverlapPair = { indexA: number; indexB: number };

function dateIsoToNumber(value: string | null | undefined): number | null {
  if (!value) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed.getTime();
}

function itemToInterval(item: TurSNSiteCustomFacetItem, isDateField: boolean): Interval | null {
  const op = item.operator ?? DEFAULT_OPERATOR;
  const start = isDateField ? dateIsoToNumber(item.rangeStartDate) : parseRangeValue(item.rangeStart);
  const end = isDateField ? dateIsoToNumber(item.rangeEndDate) : parseRangeValue(item.rangeEnd);

  switch (op) {
    case "EQUAL":
      if (start === null) return null;
      return { min: start, max: start, minInclusive: true, maxInclusive: true };
    case "GREATER_THAN":
      if (start === null) return null;
      return { min: start, max: null, minInclusive: false, maxInclusive: false };
    case "GREATER_THAN_OR_EQUAL":
      if (start === null) return null;
      return { min: start, max: null, minInclusive: true, maxInclusive: false };
    case "LESS_THAN":
      if (end === null) return null;
      return { min: null, max: end, minInclusive: false, maxInclusive: false };
    case "LESS_THAN_OR_EQUAL":
      if (end === null) return null;
      return { min: null, max: end, minInclusive: false, maxInclusive: true };
    case "BETWEEN_INCLUSIVE":
      if (start === null || end === null) return null;
      return { min: start, max: end, minInclusive: true, maxInclusive: true };
    case "BETWEEN_EXCLUSIVE":
      if (start === null || end === null) return null;
      return { min: start, max: end, minInclusive: false, maxInclusive: false };
  }
}

function intervalsOverlap(a: Interval, b: Interval): boolean {
  if (a.max !== null && b.min !== null) {
    if (a.max < b.min) return false;
    if (a.max === b.min && (!a.maxInclusive || !b.minInclusive)) return false;
  }
  if (b.max !== null && a.min !== null) {
    if (b.max < a.min) return false;
    if (b.max === a.min && (!b.maxInclusive || !a.minInclusive)) return false;
  }
  return true;
}

export function detectOverlaps(
  items: TurSNSiteCustomFacetItem[],
  isDateField: boolean,
): OverlapPair[] {
  const intervals = items.map((item) => itemToInterval(item, isDateField));
  const pairs: OverlapPair[] = [];
  for (let i = 0; i < intervals.length; i += 1) {
    const a = intervals[i];
    if (!a) continue;
    for (let j = i + 1; j < intervals.length; j += 1) {
      const b = intervals[j];
      if (!b) continue;
      if (intervalsOverlap(a, b)) {
        pairs.push({ indexA: i, indexB: j });
      }
    }
  }
  return pairs;
}
