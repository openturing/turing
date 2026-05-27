/**
 * Regenerates the backend IT fixture from the DSL bundle — the single
 * source of truth. Run after `tsc -p tsconfig.json`:
 *
 *   node scripts/emit-chaotic-fixture.mjs
 *
 * Emits the import-bundle JSON array consumed by
 * turing-app/.../harness-it/chaotic-chatflow.chat-flow.json and the
 * TurChaoticChatFlowEngineIT.
 *
 * @since 2026.3.1
 */
import { writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { transpileBundle } from "../dist/src/transpile.js";
import { chaoticBundle } from "../dist/test/fixtures/chaotic-chatflow.js";

const here = dirname(fileURLToPath(import.meta.url));
const out = resolve(
  here,
  "../../../../turing-app/src/test/resources/harness-it/chaotic-chatflow.chat-flow.json",
);

const bundle = transpileBundle([...chaoticBundle]);
writeFileSync(out, JSON.stringify(bundle, null, 2) + "\n", "utf8");
// eslint-disable-next-line no-console
console.log(`wrote ${out} (${bundle.length} flows)`);
