#!/usr/bin/env node
/**
 * Asserts `minimumReleaseAgeExclude` holds one entry per package name (T1023).
 *
 * Run from the frontend root:
 *   node scripts/check-release-age-waivers.mjs
 *
 * pnpm resolves that list by walking it and answering on the **first** rule whose
 * package name matches, never reading the rest. So a second line for a name
 * already listed is dead config: it reads like a waiver and waives nothing.
 * T1014 found that the hard way — `@viglet/viglet-design-system@2026.3.3` sat in
 * the list, correctly spelled, while every pnpm command in the workspace refused
 * it, because a `@2026.3.2` line above answered for the name first.
 *
 * The reason this needs a script rather than a convention is that `pnpm add`
 * writes the duplicate itself. Upgrading a package already in the list appends a
 * fresh line without noticing the one above it, so the waiver pnpm just wrote for
 * you is inert. Nothing else catches it: the install that wrote it succeeds,
 * because the entry is not wrong, only unreachable — the refusal arrives on some
 * later install, once the release-age window has moved.
 *
 * One failure is reported: two or more lines naming the same package. The message
 * prints them and the merged form, because the fix is always the same — join the
 * versions with `||` onto the line that already answers for that name.
 *
 * Zero-dependency by house rule, so the YAML reading is deliberately narrow: the
 * block is a flat list of `- 'name@version'` scalars. A line inside the block
 * that is not a quoted scalar or a comment is reported rather than skipped, since
 * a parser that silently drops an entry is the defect this script exists to
 * catch.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();
const FILE = "pnpm-workspace.yaml";
const BLOCK = "minimumReleaseAgeExclude:";

function fail(msg) {
  console.error(`\n  release-age waivers: ${msg}\n`);
  process.exit(1);
}

let source;
try {
  source = readFileSync(join(root, FILE), "utf8");
} catch {
  fail(`no ${FILE} here. Run this from the frontend root.`);
}

const lines = source.split(/\r?\n/);
const start = lines.findIndex((l) => l.trimEnd() === BLOCK);
if (start === -1) {
  console.log(`check-release-age-waivers: ${FILE} declares no ${BLOCK} — nothing to check.`);
  process.exit(0);
}

/**
 * The name half of a spec. Scoped packages carry an `@` of their own, so the
 * separator is the first `@` after position 0 — the same rule pnpm parses with.
 */
function packageName(spec) {
  const at = spec.startsWith("@") ? spec.indexOf("@", 1) : spec.indexOf("@");
  return at === -1 ? spec : spec.slice(0, at);
}

const entries = [];
const malformed = [];
for (let i = start + 1; i < lines.length; i++) {
  const line = lines[i];
  if (line.trim() === "") continue;
  // The block ends at the first line that is not indented under it.
  if (!/^\s/.test(line)) break;
  const trimmed = line.trim();
  if (trimmed.startsWith("#")) continue;
  const match = /^-\s*'([^']+)'\s*$/.exec(trimmed) ?? /^-\s*"([^"]+)"\s*$/.exec(trimmed);
  if (!match) {
    const bare = /^-\s*(\S+)\s*$/.exec(trimmed);
    if (bare) {
      entries.push({ spec: bare[1], line: i + 1 });
      continue;
    }
    malformed.push({ text: trimmed, line: i + 1 });
    continue;
  }
  entries.push({ spec: match[1], line: i + 1 });
}

if (malformed.length > 0) {
  fail(
    `${malformed.length} line(s) under ${BLOCK} are not list entries, and this script will not guess:\n` +
      malformed.map((m) => `    ${FILE}:${m.line}  ${m.text}`).join("\n"),
  );
}

const byName = new Map();
for (const entry of entries) {
  const name = packageName(entry.spec);
  if (!byName.has(name)) byName.set(name, []);
  byName.get(name).push(entry);
}

const shadowed = [...byName.entries()].filter(([, group]) => group.length > 1);
if (shadowed.length > 0) {
  const report = shadowed
    .map(([name, group]) => {
      // Split on `||` before de-duplicating: a line already carrying a union
      // and a fresh single-version line share a version, and the merged form
      // must name it once. That overlap is the common case, because `pnpm add`
      // re-waives a version the union above may already list.
      const versions = [
        ...new Set(
          group
            .flatMap((g) => g.spec.slice(name.length + 1).split("||"))
            .map((v) => v.trim())
            .filter(Boolean),
        ),
      ];
      const lines_ = group.map((g) => `    ${FILE}:${g.line}  ${g.spec}`).join("\n");
      return `  ${name} is named ${group.length} times; only the first is read:\n${lines_}\n` +
        `    merge them into:  '${name}@${versions.join("||")}'`;
    })
    .join("\n\n");
  fail(
    `${shadowed.length} package name(s) appear more than once.\n\n${report}\n\n` +
      "  pnpm answers on the first rule whose name matches and never reads the rest,\n" +
      "  so every line after the first is a waiver that waives nothing. `pnpm add`\n" +
      "  writes these itself when it upgrades a package already in the list.",
  );
}

console.log(
  `check-release-age-waivers: ${entries.length} waiver(s), ${byName.size} package(s), one entry each — clean.`,
);
