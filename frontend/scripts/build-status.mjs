#!/usr/bin/env node
/**
 * Post-build summary for the monorepo.
 *
 * Walks each workspace's build output directory (dist/, build/) and reports
 * file count, total size, and freshness (mtime vs now). Also detects the
 * Spring Boot public assets written by the turing-app.
 *
 * No dependencies — Node stdlib only.
 */
import fs from "node:fs";
import path from "node:path";
import url from "node:url";

const ROOT = path.resolve(path.dirname(url.fileURLToPath(import.meta.url)), "..");
const rootPkg = JSON.parse(fs.readFileSync(path.join(ROOT, "package.json"), "utf8"));

function resolveWorkspaceGlobs() {
  if (Array.isArray(rootPkg.workspaces)) return rootPkg.workspaces;
  const yamlPath = path.join(ROOT, "pnpm-workspace.yaml");
  if (fs.existsSync(yamlPath)) {
    const lines = fs.readFileSync(yamlPath, "utf8").split(/\r?\n/);
    const globs = [];
    let capture = false;
    for (const line of lines) {
      if (/^packages\s*:/.test(line)) { capture = true; continue; }
      if (capture) {
        if (/^\S/.test(line)) break;
        const m = line.match(/^\s*-\s*["']?([^"'\s#]+)["']?/);
        if (m) globs.push(m[1]);
      }
    }
    return globs;
  }
  return [];
}

const globs = resolveWorkspaceGlobs();

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

function dirStats(dir) {
  if (!fs.existsSync(dir)) return null;
  let files = 0;
  let bytes = 0;
  let newestMtime = 0;
  const stack = [dir];
  while (stack.length) {
    const cur = stack.pop();
    for (const e of fs.readdirSync(cur, { withFileTypes: true })) {
      const full = path.join(cur, e.name);
      if (e.isDirectory()) {
        stack.push(full);
      } else if (e.isFile()) {
        const st = fs.statSync(full);
        files++;
        bytes += st.size;
        if (st.mtimeMs > newestMtime) newestMtime = st.mtimeMs;
      }
    }
  }
  return { files, bytes, newestMtime };
}

function fmtSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

function fmtAge(mtimeMs) {
  const ageSec = (Date.now() - mtimeMs) / 1000;
  if (ageSec < 60) return `${ageSec.toFixed(0)}s ago`;
  if (ageSec < 3600) return `${(ageSec / 60).toFixed(0)}m ago`;
  if (ageSec < 86400) return `${(ageSec / 3600).toFixed(1)}h ago`;
  return `${(ageSec / 86400).toFixed(1)}d ago`;
}

const workspaces = globs.flatMap(expand)
  .filter((p) => fs.existsSync(path.join(p, "package.json")))
  .map((abs) => {
    const pkg = JSON.parse(fs.readFileSync(path.join(abs, "package.json"), "utf8"));
    const rel = path.relative(ROOT, abs).replaceAll("\\", "/");
    // Check both dist/ and build/
    const dist = dirStats(path.join(abs, "dist"));
    const build = dirStats(path.join(abs, "build"));
    const stats = dist || build;
    return { name: pkg.name, version: pkg.version, rel, stats, kind: dist ? "dist" : (build ? "build" : null) };
  });

const nameWidth = Math.max(...workspaces.map((w) => w.name.length + String(w.version ?? "?").length + 1));

const C = {
  reset: "\x1b[0m", dim: "\x1b[2m", green: "\x1b[32m", yellow: "\x1b[33m",
  cyan: "\x1b[36m", red: "\x1b[31m", bold: "\x1b[1m",
};
const TTY = process.stdout.isTTY;
const c = (col, s) => (TTY ? `${col}${s}${C.reset}` : s);

console.log(c(C.bold, `\n  Build status — ${path.basename(ROOT)}/`));
let totalBytes = 0;
let totalFiles = 0;
for (const w of workspaces) {
  const label = `${w.name}@${w.version}`.padEnd(nameWidth);
  if (w.stats) {
    totalBytes += w.stats.bytes;
    totalFiles += w.stats.files;
    const size = fmtSize(w.stats.bytes).padStart(9);
    const age = fmtAge(w.stats.newestMtime).padStart(8);
    console.log(`  ${c(C.green, "✓")} ${c(C.cyan, label)}  ${size}  ${c(C.dim, `${w.kind}/ · ${w.stats.files} files · ${age}`)}  ${c(C.dim, w.rel)}`);
  } else {
    console.log(`  ${c(C.dim, "·")} ${c(C.dim, label)}  ${c(C.dim, "        —  no build output                ")}  ${c(C.dim, w.rel)}`);
  }
}

// Check the Spring Boot static output (vite outDir)
const springPublic = path.resolve(ROOT, "..", "turing-app", "src", "main", "resources", "public");
const sp = dirStats(springPublic);
if (sp) {
  console.log(c(C.dim, `\n  → Spring Boot public/ (${fmtSize(sp.bytes)}, ${sp.files} files, ${fmtAge(sp.newestMtime)})`));
}

console.log(c(C.dim, `  ${c(C.bold, "Totals")}: ${fmtSize(totalBytes)} across ${totalFiles} files\n`));
