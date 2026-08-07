/**
 * Generates a turing-manifest.json file in the public/ directory.
 * Reads metadata from package.json and .env to produce a standardized
 * manifest that Turing Pages can parse during ZIP import.
 *
 * Two modes, because `public/turing-manifest.json` is tracked and the build used
 * to restamp it (T917):
 *
 *   node gen-manifest.mjs          write public/turing-manifest.json — the
 *                                  tracked, hand-editable source. No timestamp,
 *                                  so a rebuild that changes nothing writes the
 *                                  same bytes and leaves the tree clean.
 *   node gen-manifest.mjs --dist   stamp `buildDate` onto dist/turing-manifest.json,
 *                                  which vite copied out of public/. Run after
 *                                  `vite build`; this is the copy that reaches
 *                                  the ZIP, and it is not tracked.
 *
 * Usage: node gen-manifest.mjs
 * Run from within the app/ directory (where package.json and .env live).
 */
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { join } from "node:path";

const cwd = process.cwd();
const stampDist = process.argv.includes("--dist");

// T917 — the post-build half: the only place a build timestamp is written, and
// it writes into build output rather than into a tracked file.
if (stampDist) {
  const distManifest = join(cwd, "dist", "turing-manifest.json");
  if (!existsSync(distManifest)) {
    console.error(`gen-manifest --dist: ${distManifest} not found — run the build first.`);
    process.exit(1);
  }
  const built = JSON.parse(readFileSync(distManifest, "utf-8"));
  built.buildDate = new Date().toISOString();
  writeFileSync(distManifest, JSON.stringify(built, null, 2) + "\n");
  console.log(`turing-manifest.json stamped buildDate=${built.buildDate}`);
  process.exit(0);
}

const pkg = JSON.parse(readFileSync(join(cwd, "package.json"), "utf-8"));
const siteName = pkg.name.replace(/^turing-example-/, "");

// Parse .env file for VITE_ variables
const envVars = {};
const envPath = join(cwd, ".env");
if (existsSync(envPath)) {
  const envContent = readFileSync(envPath, "utf-8");
  for (const line of envContent.split("\n")) {
    const trimmed = line.trim();
    if (trimmed && !trimmed.startsWith("#")) {
      const eqIndex = trimmed.indexOf("=");
      if (eqIndex > 0) {
        const key = trimmed.substring(0, eqIndex).trim();
        const value = trimmed.substring(eqIndex + 1).trim();
        envVars[key] = value;
      }
    }
  }
}

// Read existing manifest to preserve hand-edited fields (like description)
const manifestPath = join(cwd, "public", "turing-manifest.json");
let existing = {};
if (existsSync(manifestPath)) {
  try {
    existing = JSON.parse(readFileSync(manifestPath, "utf-8"));
  } catch {
    // Ignore parse errors — regenerate from scratch
  }
}

const manifest = {
  name: siteName,
  version: pkg.version || "0.0.0",
  author: existing.author || "Alexandre Oliveira",
  repository: existing.repository || "https://github.com/openviglet/turing",
  description: existing.description || `${siteName} — Turing ES example application`,
  // No `buildDate` here — see the header. `--dist` adds it to the built copy.
  snSite: envVars.VITE_SN_SITE || siteName,
  locale: envVars.VITE_LOCALE || "en_US",
  framework: "react",
  buildTool: "vite",
};

writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + "\n");
console.log(`turing-manifest.json generated for "${siteName}" v${manifest.version}`);
