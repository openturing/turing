#!/usr/bin/env node
/**
 * Prints a per-workspace status table after `npm install` in the monorepo.
 *
 * Reads the `workspaces` globs from the root package.json, locates each
 * member's package.json, and reports:
 *   ✓ name@version   (path)         [link target, when linked via workspace]
 *
 * No dependencies — Node stdlib only.
 */
import fs from "node:fs";
import path from "node:path";
import url from "node:url";

const ROOT = path.resolve(path.dirname(url.fileURLToPath(import.meta.url)), "..");
const rootPkg = JSON.parse(fs.readFileSync(path.join(ROOT, "package.json"), "utf8"));

/** Resolve workspace globs from `package.json` (npm/yarn) or `pnpm-workspace.yaml` (pnpm). */
function resolveWorkspaceGlobs() {
  if (Array.isArray(rootPkg.workspaces)) return rootPkg.workspaces;
  const yamlPath = path.join(ROOT, "pnpm-workspace.yaml");
  if (fs.existsSync(yamlPath)) {
    const yaml = fs.readFileSync(yamlPath, "utf8");
    const inPackages = yaml.split(/\r?\n/);
    const globs = [];
    let capture = false;
    for (const line of inPackages) {
      if (/^packages\s*:/.test(line)) { capture = true; continue; }
      if (capture) {
        if (/^\S/.test(line)) break; // next top-level key
        const m = line.match(/^\s*-\s*["']?([^"'\s#]+)["']?/);
        if (m) globs.push(m[1]);
      }
    }
    return globs;
  }
  return [];
}

const globs = resolveWorkspaceGlobs();

/** Minimal glob → paths resolver. Supports trailing `/*` only (enough here). */
function expand(glob) {
  if (glob.endsWith("/*")) {
    const parent = path.join(ROOT, glob.slice(0, -2));
    if (!fs.existsSync(parent)) return [];
    return fs.readdirSync(parent, { withFileTypes: true })
      .filter((e) => e.isDirectory())
      .map((e) => path.join(parent, e.name));
  }
  const p = path.join(ROOT, glob);
  return fs.existsSync(p) ? [p] : [];
}

const workspaces = globs.flatMap(expand)
  .filter((p) => fs.existsSync(path.join(p, "package.json")))
  .map((abs) => {
    const pkg = JSON.parse(fs.readFileSync(path.join(abs, "package.json"), "utf8"));
    const rel = path.relative(ROOT, abs).replaceAll("\\", "/");
    return {
      name: pkg.name,
      version: pkg.version ?? "?",
      rel,
      private: !!pkg.private,
      abs,
    };
  });

const nameWidth = Math.max(...workspaces.map((w) => w.name.length + w.version.length + 1));

const C = {
  reset: "\x1b[0m",
  dim: "\x1b[2m",
  green: "\x1b[32m",
  yellow: "\x1b[33m",
  cyan: "\x1b[36m",
};
const TTY = process.stdout.isTTY;
const c = (col, s) => (TTY ? `${col}${s}${C.reset}` : s);

console.log(c(C.dim, `\n  Workspaces (${workspaces.length}) under ${path.basename(ROOT)}/`));
for (const w of workspaces) {
  const label = `${w.name}@${w.version}`.padEnd(nameWidth);
  const tag = w.private ? c(C.yellow, " private") : c(C.green, " public ");
  console.log(`  ${c(C.green, "✓")} ${c(C.cyan, label)} ${tag}  ${c(C.dim, w.rel)}`);
}

// Verify the SDK is symlinked (workspace resolution healthy)
const nm = path.join(ROOT, "node_modules", "@viglet", "turing-react-sdk");
if (fs.existsSync(nm)) {
  const stat = fs.lstatSync(nm);
  if (stat.isSymbolicLink()) {
    const target = path.relative(ROOT, fs.realpathSync(nm)).replaceAll("\\", "/");
    console.log(c(C.dim, `  ↳ @viglet/turing-react-sdk → ${target}`));
  } else {
    console.log(c(C.yellow, `  ⚠ @viglet/turing-react-sdk is NOT symlinked — workspace resolution may be broken`));
  }
}

console.log();
