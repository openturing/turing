/**
 * Generates a turing-manifest.json file in the public/ directory.
 * Reads metadata from package.json and .env to produce a standardized
 * manifest that Turing Pages can parse during ZIP import.
 *
 * Usage: node gen-manifest.mjs
 * Run from within the app/ directory (where package.json and .env live).
 */
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { join } from "node:path";

const cwd = process.cwd();
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
  buildDate: new Date().toISOString(),
  snSite: envVars.VITE_SN_SITE || siteName,
  locale: envVars.VITE_LOCALE || "en_US",
  framework: "react",
  buildTool: "vite",
};

writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + "\n");
console.log(`turing-manifest.json generated for "${siteName}" v${manifest.version}`);
