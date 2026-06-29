/**
 * Generates public/turing-manifest.json for the Atlas Store showcase.
 *
 * Reads metadata from package.json and .env to produce the standardized
 * manifest that Turing Pages parses during ZIP import. Adapted from the
 * marketplace examples' shared gen-manifest.mjs, but self-contained because the
 * showcase lives outside apps/marketplace.
 *
 * Usage: node gen-manifest.mjs   (run from the app directory)
 */
import { readFileSync, writeFileSync, existsSync, mkdirSync } from "node:fs";
import { join } from "node:path";

const cwd = process.cwd();
const pkg = JSON.parse(readFileSync(join(cwd, "package.json"), "utf-8"));

const envVars = {};
const envPath = join(cwd, ".env");
if (existsSync(envPath)) {
  for (const line of readFileSync(envPath, "utf-8").split("\n")) {
    const trimmed = line.trim();
    if (trimmed && !trimmed.startsWith("#")) {
      const eq = trimmed.indexOf("=");
      if (eq > 0) envVars[trimmed.slice(0, eq).trim()] = trimmed.slice(eq + 1).trim();
    }
  }
}

const siteName = envVars.VITE_SN_SITE || "atlas-store";
const publicDir = join(cwd, "public");
mkdirSync(publicDir, { recursive: true });
const manifestPath = join(publicDir, "turing-manifest.json");

let existing = {};
if (existsSync(manifestPath)) {
  try {
    existing = JSON.parse(readFileSync(manifestPath, "utf-8"));
  } catch {
    /* regenerate from scratch */
  }
}

const manifest = {
  name: siteName,
  version: pkg.version || "0.0.0",
  author: existing.author || "Alexandre Oliveira",
  repository: existing.repository || "https://github.com/openviglet/turing",
  description:
    existing.description ||
    "Atlas Store — the Viglet Turing ES reference showcase (stresses the maximum platform surface).",
  buildDate: new Date().toISOString(),
  snSite: siteName,
  locale: envVars.VITE_LOCALE || "en_US",
  framework: "react",
  buildTool: "vite",
};

writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + "\n");
console.log(`turing-manifest.json generated for "${siteName}" v${manifest.version}`);
