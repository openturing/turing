/**
 * Emits the PUBLIC model catalog artifact (Block BC / T759) from the single
 * source of truth — turing-app's bundled `models-catalog.json` (the enriched
 * envelope). Validates the source structurally (zero-dep, no ajv), flattens each
 * vendor's entries adding a `vendor` field, and writes the published JSON + a
 * pinned version copy + the JSON Schema into the site's public dir so the site
 * build (T760) serves them at turing.viglet.org/models/*.
 *
 *   node scripts/emit-model-catalog.mjs
 *
 * @since 2026.3.4 (T759)
 */
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const SRC = resolve(here, "../../../../turing-app/src/main/resources/llm/models-catalog.json");
const SCHEMA_SRC = resolve(here, "../../../../turing-app/src/main/resources/llm/models-catalog.schema.json");
const OUT_DIR = resolve(here, "../public/models");
const SOURCE_URL = "https://turing.viglet.org";

const KINDS = new Set([
  "CHAT", "EMBEDDING", "RERANK", "IMAGE",
  "TRANSCRIPTION", "SPEECH", "VIDEO", "MODERATION", "UNKNOWN",
]);

// Additive lifecycle stages (Block BD / T764). Optional; absent for hand entries.
const STATUSES = new Set(["PREVIEW", "GA", "DEPRECATED", "RETIRED"]);

function fail(msg) {
  console.error(`emit-model-catalog: ${msg}`);
  process.exit(1);
}

const root = JSON.parse(readFileSync(SRC, "utf8"));

if (typeof root.version !== "number") fail("source missing integer `version`");
if (typeof root.lastUpdated !== "string") fail("source missing `lastUpdated`");
if (typeof root.vendors !== "object" || root.vendors === null) fail("source missing `vendors` object");

// Validate + flatten (add `vendor` to each entry).
const vendors = {};
let count = 0;
for (const [vendor, entries] of Object.entries(root.vendors)) {
  if (!Array.isArray(entries)) fail(`vendor "${vendor}" is not an array`);
  vendors[vendor] = entries.map((e, i) => {
    const where = `${vendor}[${i}]`;
    if (!e.id || typeof e.id !== "string") fail(`${where} missing string \`id\``);
    if (!e.label || typeof e.label !== "string") fail(`${where} (${e.id}) missing \`label\``);
    if (!KINDS.has(e.kind)) fail(`${where} (${e.id}) has invalid kind "${e.kind}"`);
    // Additive fields (Block BD / T764) — optional; validate only when present.
    if (e.status !== undefined && !STATUSES.has(e.status)) fail(`${where} (${e.id}) has invalid status "${e.status}"`);
    if (e.maxOutputTokens !== undefined && !(Number.isInteger(e.maxOutputTokens) && e.maxOutputTokens >= 1)) fail(`${where} (${e.id}) has invalid maxOutputTokens`);
    if (e.aliases !== undefined && !Array.isArray(e.aliases)) fail(`${where} (${e.id}) \`aliases\` must be an array`);
    if (e.sources !== undefined && !Array.isArray(e.sources)) fail(`${where} (${e.id}) \`sources\` must be an array`);
    if (e.modalities !== undefined && (typeof e.modalities !== "object" || e.modalities === null || Array.isArray(e.modalities))) fail(`${where} (${e.id}) \`modalities\` must be an object`);
    count++;
    return { ...e, vendor };
  });
}

const published = {
  $schema: `${SOURCE_URL}/models/catalog.schema.json`,
  version: root.version,
  lastUpdated: root.lastUpdated,
  source: SOURCE_URL,
  vendors,
};

mkdirSync(OUT_DIR, { recursive: true });
const json = JSON.stringify(published, null, 2) + "\n";
writeFileSync(resolve(OUT_DIR, "catalog.json"), json, "utf8"); // rolling latest
writeFileSync(resolve(OUT_DIR, `catalog-v${root.version}.json`), json, "utf8"); // pinned
writeFileSync(resolve(OUT_DIR, "catalog.schema.json"), readFileSync(SCHEMA_SRC, "utf8"), "utf8");

console.log(
  `emit-model-catalog: wrote catalog.json + catalog-v${root.version}.json + schema ` +
    `(${Object.keys(vendors).length} vendors, ${count} models) to ${OUT_DIR}`,
);
