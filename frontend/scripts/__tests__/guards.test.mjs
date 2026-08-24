/**
 * Tests for the three scripts that decide whether a package ships (T935).
 *
 * `check-size.mjs`, `check-package-contents.mjs` and `check-overrides.mjs` are
 * the gates every other guarantee in Block BV rests on, and until now the only
 * evidence they worked was a mutation test run by hand once, at the moment each
 * was written. These are those mutations, kept.
 *
 * Each script is run as a subprocess against a fixture directory, because that is
 * what CI does: the contract under test is the **exit code** and the message, not
 * an internal function. Fixtures are written to a temp directory, so the suite
 * needs no install and touches no workspace.
 *
 * `node --test scripts/__tests__` from the frontend root.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
import { deepEqual, match, ok, strictEqual } from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { after, describe, it } from "node:test";
import { gzipSync } from "node:zlib";

const SCRIPTS = dirname(dirname(fileURLToPath(import.meta.url)));
const roots = [];

/** A throwaway directory, cleaned up when the suite ends. */
function fixture(name) {
  const dir = mkdtempSync(join(tmpdir(), `turing-${name}-`));
  roots.push(dir);
  return dir;
}

function write(dir, relative, contents) {
  const full = join(dir, relative);
  mkdirSync(dirname(full), { recursive: true });
  writeFileSync(full, contents);
  return full;
}

/** Runs a guard in `dir` and returns {code, out}. */
function run(script, dir) {
  const r = spawnSync(process.execPath, [join(SCRIPTS, script)], {
    cwd: dir,
    encoding: "utf8",
  });
  return { code: r.status, out: `${r.stdout ?? ""}${r.stderr ?? ""}` };
}

after(() => {
  for (const dir of roots) {
    try {
      rmSync(dir, { recursive: true, force: true });
    } catch {
      /* a temp directory that outlives the run is not a failed test */
    }
  }
});

// ── check-overrides.mjs ──────────────────────────────────────────────────────

/** A workspace + lockfile pair, with `overrides` and resolved versions. */
function overridesFixture(overrides, resolved) {
  const dir = fixture("overrides");
  const block = Object.entries(overrides)
    .map(([k, v]) => `  "${k}": "${v}"`)
    .join("\n");
  write(dir, "pnpm-workspace.yaml", `packages:\n  - "packages/*"\n\noverrides:\n${block}\n`);
  const packages = resolved
    .map((spec) => `  ${spec.startsWith("@") ? `'${spec}'` : spec}:\n    resolution: {integrity: sha512-x}\n`)
    .join("\n");
  write(dir, "pnpm-lock.yaml", `lockfileVersion: '9.0'\n\npackages:\n\n${packages}\n`);
  return dir;
}

describe("check-overrides", () => {
  it("passes when every override is resolved and in force", () => {
    const dir = overridesFixture({ undici: ">=7.28.0 <8" }, ["undici@7.29.0"]);
    const { code, out } = run("check-overrides.mjs", dir);
    strictEqual(code, 0, out);
    match(out, /every override resolves and is in force/);
  });

  it("fails when a resolved version does not satisfy its override", () => {
    const dir = overridesFixture({ postcss: ">=9.0.0" }, ["postcss@8.5.26"]);
    const { code, out } = run("check-overrides.mjs", dir);
    strictEqual(code, 1, out);
    match(out, /"postcss" declares ">=9\.0\.0" but the lockfile resolves 8\.5\.26/);
  });

  it("fails when an override names a package the lockfile never resolves", () => {
    const dir = overridesFixture({ "adm-zip-typo": ">=0.6.0" }, ["adm-zip@0.6.0"]);
    const { code, out } = run("check-overrides.mjs", dir);
    strictEqual(code, 1, out);
    match(out, /which the lockfile never resolves/);
  });

  it("reads a scoped package, which the lockfile quotes", () => {
    const dir = overridesFixture({ "@babel/core": ">=7.29.6 <8" }, ["@babel/core@7.29.7"]);
    const { code, out } = run("check-overrides.mjs", dir);
    strictEqual(code, 0, out);
    match(out, /7\.29\.7/);
  });

  it("reports a version-selector key that selects nothing, without failing", () => {
    // T934 — a security floor kept after its dependency left is deliberate.
    const dir = overridesFixture({ "js-yaml@3": ">=3.15.0" }, ["js-yaml@4.3.1"]);
    const { code, out } = run("check-overrides.mjs", dir);
    strictEqual(code, 0, out);
    match(out, /selects nothing/);
  });

  it("does not judge a path-scoped key against unrelated copies", () => {
    // `read-yaml-file>js-yaml` governs one copy; `packages:` is flat and cannot
    // say which, so comparing it to every copy would fail on an unrelated one.
    const dir = overridesFixture({ "read-yaml-file>js-yaml": ">=5.2.2 <6" }, [
      "js-yaml@4.3.1",
      "js-yaml@5.2.3",
    ]);
    const { code, out } = run("check-overrides.mjs", dir);
    strictEqual(code, 0, out);
    match(out, /path-scoped, not verifiable here/);
  });

  it("refuses a lockfile it parsed nothing out of, rather than passing", () => {
    // The silent failure the script exists to prevent, applied to itself: a
    // future lockfile format must fail loudly, not report zero violations.
    const dir = fixture("overrides-empty");
    write(dir, "pnpm-workspace.yaml", 'packages:\n  - "packages/*"\n\noverrides:\n  "undici": ">=7.0.0"\n');
    write(dir, "pnpm-lock.yaml", "lockfileVersion: '99.0'\n\nsomethingElse:\n  x: 1\n");
    const { code, out } = run("check-overrides.mjs", dir);
    strictEqual(code, 1, out);
    match(out, /parsed no packages/);
  });
});

// ── check-size.mjs ───────────────────────────────────────────────────────────

/** A package whose `dist` holds files of a known gzip size. */
function sizeFixture(sizeBudget, files) {
  const dir = fixture("size");
  write(dir, "package.json", JSON.stringify({ name: "fixture-pkg", version: "1.0.0", sizeBudget }, null, 2));
  for (const [rel, contents] of Object.entries(files)) write(dir, join("dist", rel), contents);
  return dir;
}

/** Gzip length of a string, so a fixture can state an exact baseline. */
const gz = (s) => gzipSync(Buffer.from(s), { level: 9 }).length;

describe("check-size", () => {
  const body = "export const a = 1;\n".repeat(50);

  it("passes when every entry is within its baseline delta", () => {
    const dir = sizeFixture(
      { strict: true, delta: { entry: 512, total: 1024 }, baseline: { measured: "2026-01-01", total: gz(body), entries: { "index.js": gz(body) } } },
      { "index.js": body },
    );
    const { code, out } = run("check-size.mjs", dir);
    strictEqual(code, 0, out);
    match(out, /within budget/);
  });

  it("fails when an entry grows past its allowance", () => {
    const dir = sizeFixture(
      { strict: true, delta: { entry: 16, total: 16 }, baseline: { measured: "2026-01-01", total: gz(body), entries: { "index.js": gz(body) } } },
      { "index.js": `${body}${"export const filler = 2;\n".repeat(200)}` },
    );
    const { code, out } = run("check-size.mjs", dir);
    strictEqual(code, 1, out);
    match(out, /index\.js grew .* over its .* baseline/);
  });

  it("names the directory that grew, not just the total (T878)", () => {
    const dir = sizeFixture(
      {
        strict: false,
        groupBy: "dir",
        delta: { entry: 512, dir: 16, total: 100000 },
        baseline: { measured: "2026-01-01", total: 999999, dirs: { "hooks/": gz(body), "(root)": gz(body) } },
      },
      { "index.js": body, "hooks/use-a.js": `${body}${"export const filler = 2;\n".repeat(200)}` },
    );
    const { code, out } = run("check-size.mjs", dir);
    strictEqual(code, 1, out);
    match(out, /hooks\/ grew/);
    ok(!/\(root\) grew/.test(out), "the untouched directory must not be reported");
  });

  it("fails a directory that has no baseline at all", () => {
    const dir = sizeFixture(
      { strict: false, groupBy: "dir", baseline: { measured: "2026-01-01", total: 999999, dirs: {} } },
      { "index.js": body },
    );
    const { code, out } = run("check-size.mjs", dir);
    strictEqual(code, 1, out);
    match(out, /has no baseline/);
  });

  // T1010 — the age of the number the delta is measured against. When T1005
  // tripped this gate, 0.66 kB of the 0.93 kB predated the change and the output
  // gave no way to see that, so the author inherited three weeks of drift to
  // justify. Nothing runs this between releases (every workflow here is
  // workflow_dispatch on purpose), so a stale baseline is the normal case.
  it("reports the baseline's age, so a delta is read against a date", () => {
    const dir = sizeFixture(
      { strict: true, delta: { entry: 512, total: 1024 }, baseline: { measured: "2026-01-01", total: gz(body), entries: { "index.js": gz(body) } } },
      { "index.js": body },
    );
    const { code, out } = run("check-size.mjs", dir);
    strictEqual(code, 0, out);
    match(out, /baseline measured 2026-01-01, \d+d ago/);
  });

  it("says a passing delta was measured against a stale number", () => {
    const dir = sizeFixture(
      { strict: true, delta: { entry: 512, total: 1024 }, baseline: { measured: "2026-01-01", total: gz(body), entries: { "index.js": gz(body) } } },
      { "index.js": body },
    );
    const { code, out } = run("check-size.mjs", dir);
    strictEqual(code, 0, out);
    match(out, /the baseline is \d+ days old/);
  });

  it("names the stash procedure when a stale baseline is exceeded", () => {
    const grown = `${body}${"export const b = 2;\n".repeat(400)}`;
    const dir = sizeFixture(
      { strict: true, delta: { entry: 8, total: 8 }, baseline: { measured: "2026-01-01", total: gz(body), entries: { "index.js": gz(body) } } },
      { "index.js": grown },
    );
    const { code, out } = run("check-size.mjs", dir);
    strictEqual(code, 1, out);
    match(out, /may predate this/);
    match(out, /git stash push -u/);
  });

  it("stays quiet about age when the baseline is recent", () => {
    const today = new Date().toISOString().slice(0, 10);
    const dir = sizeFixture(
      { strict: true, delta: { entry: 512, total: 1024 }, baseline: { measured: today, total: gz(body), entries: { "index.js": gz(body) } } },
      { "index.js": body },
    );
    const { code, out } = run("check-size.mjs", dir);
    strictEqual(code, 0, out);
    ok(!/days old/.test(out), "a fresh baseline must not be called stale");
  });

  it("honours a package's own staleAfterDays", () => {
    const dir = sizeFixture(
      { strict: true, staleAfterDays: 100000, delta: { entry: 512, total: 1024 }, baseline: { measured: "2026-01-01", total: gz(body), entries: { "index.js": gz(body) } } },
      { "index.js": body },
    );
    const { code, out } = run("check-size.mjs", dir);
    strictEqual(code, 0, out);
    ok(!/days old/.test(out), "a package that widened the window must not be nagged");
  });
});

// ── check-package-contents.mjs ───────────────────────────────────────────────

/** A publishable package, so `npm pack --dry-run` has something to list. */
function contentsFixture(pkg, files) {
  const dir = fixture("contents");
  write(dir, "package.json", JSON.stringify({ name: "fixture-pkg", version: "1.0.0", ...pkg }, null, 2));
  for (const [rel, contents] of Object.entries(files)) write(dir, rel, contents);
  return dir;
}

describe("check-package-contents", () => {
  it("passes a package that ships its entry points and nothing else", () => {
    const dir = contentsFixture(
      { main: "dist/index.js", types: "dist/index.d.ts", files: ["dist/**/*"] },
      { "dist/index.js": "export const a = 1;\n", "dist/index.d.ts": "export declare const a: number;\n" },
    );
    const { code, out } = run("check-package-contents.mjs", dir);
    strictEqual(code, 0, out);
    match(out, /ships every entry point/);
  });

  it("fails a package whose tarball would carry a test file (T915, T919)", () => {
    const dir = contentsFixture(
      { main: "dist/index.js", files: ["dist/**/*"] },
      { "dist/index.js": "export const a = 1;\n", "dist/test/deploy.test.js": "// compiled test\n" },
    );
    const { code, out } = run("check-package-contents.mjs", dir);
    strictEqual(code, 1, out);
    match(out, /must not be published/);
  });

  it("fails a package whose tarball would carry a source map (T920)", () => {
    const dir = contentsFixture(
      { main: "dist/index.js", files: ["dist/**/*"] },
      { "dist/index.js": "export const a = 1;\n", "dist/index.js.map": '{"version":3,"sources":["../src/index.ts"]}\n' },
    );
    const { code, out } = run("check-package-contents.mjs", dir);
    strictEqual(code, 1, out);
    match(out, /must not be published/);
  });

  it("fails when `files` is narrowed past a declared entry point", () => {
    // The failure mode the fix for the previous two creates.
    const dir = contentsFixture(
      { main: "dist/index.js", types: "dist/index.d.ts", files: ["dist/index.js"] },
      { "dist/index.js": "export const a = 1;\n", "dist/index.d.ts": "export declare const a: number;\n" },
    );
    const { code, out } = run("check-package-contents.mjs", dir);
    strictEqual(code, 1, out);
    match(out, /"types" points at dist\/index\.d\.ts, which "files" does not publish/);
  });

  it("skips a private package, which is never published", () => {
    const dir = contentsFixture({ private: true, main: "dist/index.js" }, { "dist/index.js": "export const a = 1;\n" });
    const { code, out } = run("check-package-contents.mjs", dir);
    strictEqual(code, 0, out);
    match(out, /is private/);
  });
});

// A guard on the guards: every script under test must exist where the tests
// expect it, so a rename shows up here rather than as a suite that runs nothing.
describe("the scripts under test", () => {
  it("are all present", () => {
    const names = [
      "check-size.mjs",
      "check-package-contents.mjs",
      "check-overrides.mjs",
      // T948 — a rule set rather than an executable script, but the same
      // rename hazard: its suite would run nothing.
      "search-contract-rules.mjs",
    ];
    deepEqual(
      names.filter((n) => existsSync(join(SCRIPTS, n))),
      names,
    );
  });
});

// ── check-release-age-waivers.mjs ────────────────────────────────────────────

/** A workspace file whose exclude block holds exactly these specs. */
function waiversFixture(specs, tail = "") {
  const dir = fixture("waivers");
  const block = specs.map((s) => `  - '${s}'`).join("\n");
  write(
    dir,
    "pnpm-workspace.yaml",
    `packages:\n  - "apps/*"\n\nminimumReleaseAgeExclude:\n${block}\n${tail}\noverrides:\n  "x": "1"\n`,
  );
  return dir;
}

describe("check-release-age-waivers", () => {
  it("passes when every package is named once", () => {
    const dir = waiversFixture(["a@1.0.0", "@scope/b@2.0.0", "c@3.0.0"]);
    const { code, out } = run("check-release-age-waivers.mjs", dir);
    strictEqual(code, 0);
    match(out, /3 waiver\(s\), 3 package\(s\)/);
  });

  it("fails on the duplicate `pnpm add` writes, and names both lines", () => {
    // The real shape from T1023: a union line, then a fresh single-version line
    // appended below it for a version the union may already carry.
    const dir = waiversFixture([
      "@scope/ds@1.0.0||1.1.0",
      "other@9.9.9",
      "@scope/ds@1.2.0",
    ]);
    const { code, out } = run("check-release-age-waivers.mjs", dir);
    strictEqual(code, 1);
    match(out, /@scope\/ds is named 2 times/);
    // Both offending lines are located, so the reader does not go hunting.
    match(out, /pnpm-workspace\.yaml:5\b/);
    match(out, /pnpm-workspace\.yaml:7\b/);
  });

  it("suggests a merged line that names each version once", () => {
    // 1.1.0 is on both lines; the merged form must not repeat it.
    const dir = waiversFixture(["@scope/ds@1.0.0||1.1.0", "@scope/ds@1.1.0"]);
    const { out } = run("check-release-age-waivers.mjs", dir);
    match(out, /merge them into: {2}'@scope\/ds@1\.0\.0\|\|1\.1\.0'/);
  });

  it("reads a scoped name past its own leading @", () => {
    // `@scope/a` and `@scope/b` are different packages; splitting on the first
    // `@` of the string would call them both "" and report a false duplicate.
    const dir = waiversFixture(["@scope/a@1.0.0", "@scope/b@1.0.0"]);
    strictEqual(run("check-release-age-waivers.mjs", dir).code, 0);
  });

  it("refuses a line under the block it cannot parse, rather than dropping it", () => {
    const dir = fixture("waivers-malformed");
    write(
      dir,
      "pnpm-workspace.yaml",
      `minimumReleaseAgeExclude:\n  - 'a@1.0.0'\n  - { name: b }\n`,
    );
    const { code, out } = run("check-release-age-waivers.mjs", dir);
    strictEqual(code, 1);
    match(out, /not list entries/);
  });

  it("passes a workspace that declares no exclude block at all", () => {
    const dir = fixture("waivers-absent");
    write(dir, "pnpm-workspace.yaml", `packages:\n  - "apps/*"\n`);
    const { code, out } = run("check-release-age-waivers.mjs", dir);
    strictEqual(code, 0);
    match(out, /declares no minimumReleaseAgeExclude/);
  });
});
