/**
 * Display helpers for the Atlas Store catalog. The catalog stores prices both
 * as a CURRENCY field (`price` = "29.99,USD") and a numeric FLOAT mirror
 * (`price_amount`); the UI reads the numeric mirror for robust formatting.
 */

/** Coerce a possibly-array / possibly-string raw field to a single string. */
export function firstValue(value: unknown): string {
  if (Array.isArray(value)) return value.length ? String(value[0]) : "";
  return value == null ? "" : String(value);
}

/** Coerce a raw field to a number, or undefined when not numeric. */
export function toNumber(value: unknown): number | undefined {
  const v = Array.isArray(value) ? value[0] : value;
  if (v == null || v === "") return undefined;
  const n = typeof v === "number" ? v : Number(String(v).replace(/,.*$/, ""));
  return Number.isFinite(n) ? n : undefined;
}

/** Format an amount + ISO 4217 code as a localized currency string. */
export function formatPrice(amount: number | undefined, currency = "USD"): string {
  if (amount == null) return "";
  try {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency,
    }).format(amount);
  } catch {
    return `${amount.toFixed(2)} ${currency}`;
  }
}

/** Round a 0–5 rating to the nearest half for star rendering. */
export function toStars(rating: number | undefined): { full: number; half: boolean } {
  const r = Math.max(0, Math.min(5, rating ?? 0));
  const full = Math.floor(r);
  return { full, half: r - full >= 0.5 };
}
