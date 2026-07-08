import { readdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * i18n key-parity guard.
 *
 * Every user-facing string in the app is looked up with `t("namespace.key")`.
 * When a key exists in one locale but not the other, i18next silently falls
 * back — most visibly to the English `defaultValue` baked into the call site —
 * so a missing `pt` translation renders as English and nobody notices until a
 * Portuguese user reports it page by page.
 *
 * This test flattens every `locales/en/*.json` and `locales/pt/*.json` bundle
 * into dotted key paths and asserts the two sets are identical. A new key added
 * to only one locale fails CI here instead of shipping as an untranslated
 * string. Keep the locales symmetric: add each key to BOTH `en` and `pt`.
 */
const localesDir = join(dirname(fileURLToPath(import.meta.url)), "..", "locales");

type Json = Record<string, unknown>;

/** Merge every namespace file for a locale into one object (mirrors index.ts). */
function loadLocale(lang: "en" | "pt"): Json {
  const dir = join(localesDir, lang);
  const merged: Json = {};
  for (const file of readdirSync(dir).filter((f) => f.endsWith(".json"))) {
    const content = JSON.parse(readFileSync(join(dir, file), "utf-8")) as Json;
    Object.assign(merged, content);
  }
  return merged;
}

/** Collect the dotted paths of every leaf (string) value. */
function flattenKeys(obj: Json, prefix = ""): string[] {
  const keys: string[] = [];
  for (const [key, value] of Object.entries(obj)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (value !== null && typeof value === "object" && !Array.isArray(value)) {
      keys.push(...flattenKeys(value as Json, path));
    } else {
      keys.push(path);
    }
  }
  return keys;
}

describe("i18n locale parity (en ↔ pt)", () => {
  const enKeys = new Set(flattenKeys(loadLocale("en")));
  const ptKeys = new Set(flattenKeys(loadLocale("pt")));

  it("has every English key translated in Portuguese", () => {
    const missingInPt = [...enKeys].filter((k) => !ptKeys.has(k)).sort();
    expect(missingInPt, `Keys present in en but missing in pt:\n${missingInPt.join("\n")}`).toEqual([]);
  });

  it("has no Portuguese key without an English counterpart", () => {
    const missingInEn = [...ptKeys].filter((k) => !enKeys.has(k)).sort();
    expect(missingInEn, `Keys present in pt but missing in en:\n${missingInEn.join("\n")}`).toEqual([]);
  });
});
