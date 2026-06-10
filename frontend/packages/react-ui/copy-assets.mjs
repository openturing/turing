#!/usr/bin/env node
/**
 * Copies non-TS static assets into `dist` after the `tsc` build.
 *
 * `tsc` only emits the compiled JS/d.ts; the optional token-theming stylesheet
 * (T305) is a hand-authored CSS file, so it has to be copied across so it ships
 * in the published `dist` (referenced by the `./styles.css` export). Zero-dep,
 * cross-platform (used on the Windows dev box too).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
import { copyFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));

/** [from (relative to package root), to (relative to package root)] */
const assets = [["src/styles/turing-ui.css", "dist/styles/turing-ui.css"]];

for (const [from, to] of assets) {
  const dest = join(root, to);
  mkdirSync(dirname(dest), { recursive: true });
  copyFileSync(join(root, from), dest);
  console.log(`  copied ${from} → ${to}`);
}
