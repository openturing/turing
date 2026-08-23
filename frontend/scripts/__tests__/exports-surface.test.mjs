/**
 * Tests for `check-exports.mjs` — the gate that decides whether a package's
 * public surface may ship under its current version (T1006).
 *
 * The bug it exists for: `@viglet/turing-sdk` set `version: 2026.3.7` on
 * 2026-07-13, `postSearch` and the T710 params bridges landed on 2026-08-02, and
 * a consumer installing 2026.3.7 got a package whose `dist` did not contain the
 * symbols the source and the docs advertised. Building faceted search against it
 * meant hand-writing the facet toggling and the params bridge that the package's
 * own comment says must not be duplicated.
 *
 * Both guards are mutated here, because the interesting half is which one fires.
 * "Is every source export in the built dist?" would have passed the whole time —
 * the build was never wrong. What distinguishes healthy from broken is whether a
 * pending changeset claims the growth, and that is the case worth keeping.
 *
 * Run as a subprocess, like CI does: the contract is the exit code and the
 * message, not an internal function.
 *
 * `node --test scripts/__tests__` from the frontend root.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
import { match, strictEqual } from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { after, describe, it } from "node:test";

const SCRIPTS = dirname(dirname(fileURLToPath(import.meta.url)));
const roots = [];

after(() => {
  for (const dir of roots) {
    try {
      rmSync(dir, { recursive: true, force: true });
    } catch {
      /* a temp directory that outlives the run is not a failed test */
    }
  }
});

function write(dir, relative, contents) {
  const full = join(dir, relative);
  mkdirSync(dirname(full), { recursive: true });
  writeFileSync(full, contents);
}

/**
 * A package whose released surface was `released`, whose build now exports
 * `built`, and whose sibling `.changeset` holds `changesets`.
 *
 * The layout mirrors the real one — the package sits two levels under the root
 * that owns `.changeset`, which is what the script's default path assumes.
 */
function pkgFixture({ released, built, source = built, changesets = {}, version = "1.0.0" }) {
  const root = mkdtempSync(join(tmpdir(), "turing-exports-"));
  roots.push(root);
  const dir = join(root, "packages", "sdk");
  write(dir, "package.json", `${JSON.stringify({
    name: "@viglet/turing-sdk",
    version,
    exportsSurface: {
      entry: "dist/index.d.ts",
      source: "src/index.ts",
      baseline: { version, recorded: "2026-07-13", symbols: released },
    },
  }, null, 2)}\n`);
  const barrel = (names) => `${names.map((n) => `export { ${n} } from "./x";`).join("\n")}\n`;
  write(dir, "dist/index.d.ts", barrel(built));
  write(dir, "src/index.ts", barrel(source));
  for (const [name, body] of Object.entries(changesets)) {
    write(root, join(".changeset", name), body);
  }
  return dir;
}

function run(dir) {
  const r = spawnSync(process.execPath, [join(SCRIPTS, "check-exports.mjs")], {
    cwd: dir,
    encoding: "utf8",
  });
  return { code: r.status, out: `${r.stdout ?? ""}${r.stderr ?? ""}` };
}

const BUMP = '---\n"@viglet/turing-sdk": minor\n---\n\nT709 — POST-native search.\n';

describe("check-exports: growth past the last release", () => {
  it("rejects a symbol added under a version that is already taken", () => {
    const dir = pkgFixture({
      released: ["fetchSearch"],
      built: ["fetchSearch", "postSearch"],
    });

    const { code, out } = run(dir);

    strictEqual(code, 1, out);
    match(out, /export surface rejected/);
    match(out, /no pending changeset bumps @viglet\/turing-sdk/);
    match(out, /added: postSearch/);
  });

  it("allows it once a changeset claims the bump", () => {
    // The real state of the repository: T709/T710 both wrote changesets naming
    // this package, so the growth is declared and a release will apply it. The
    // failure above and this success look identical to every other check.
    const dir = pkgFixture({
      released: ["fetchSearch"],
      built: ["fetchSearch", "postSearch"],
      changesets: { "t709-post-native-search.md": BUMP },
    });

    const { code, out } = run(dir);

    strictEqual(code, 0, out);
    match(out, /1 unreleased symbol\(s\) past 1\.0\.0/);
  });

  it("ignores a changeset that bumps some other package", () => {
    const dir = pkgFixture({
      released: ["fetchSearch"],
      built: ["fetchSearch", "postSearch"],
      changesets: {
        "t710-react.md": '---\n"@viglet/turing-react-sdk": minor\n---\n\nT710.\n',
      },
    });

    const { code, out } = run(dir);

    strictEqual(code, 1, out);
    match(out, /no pending changeset bumps @viglet\/turing-sdk/);
  });

  it("reports a removed symbol too, which is a break rather than an addition", () => {
    const dir = pkgFixture({
      released: ["fetchSearch", "parseHrefToParams"],
      built: ["fetchSearch"],
    });

    const { code, out } = run(dir);

    strictEqual(code, 1, out);
    match(out, /removed: parseHrefToParams/);
  });

  it("passes when the surface matches the release exactly", () => {
    const dir = pkgFixture({
      released: ["fetchSearch", "postClick"],
      built: ["fetchSearch", "postClick"],
    });

    const { code, out } = run(dir);

    strictEqual(code, 0, out);
    match(out, /matches the last release/);
  });
});

describe("check-exports: the build carries the source surface", () => {
  it("rejects a source export the build dropped", () => {
    // The guard that would NOT have caught T1006, kept because a build that
    // silently drops an entry point is its own real failure mode.
    const dir = pkgFixture({
      released: ["fetchSearch"],
      built: ["fetchSearch"],
      source: ["fetchSearch", "postSearch"],
    });

    const { code, out } = run(dir);

    strictEqual(code, 1, out);
    match(out, /postSearch is exported by src\/index\.ts and absent from the build/);
  });

  it("fails loudly when the build has not been run", () => {
    const root = mkdtempSync(join(tmpdir(), "turing-exports-"));
    roots.push(root);
    const dir = join(root, "packages", "sdk");
    write(dir, "package.json", `${JSON.stringify({
      name: "@viglet/turing-sdk",
      version: "1.0.0",
      exportsSurface: { entry: "dist/index.d.ts", source: "src/index.ts" },
    })}\n`);
    write(dir, "src/index.ts", 'export { fetchSearch } from "./x";\n');

    const { code, out } = run(dir);

    strictEqual(code, 1, out);
    match(out, /build the package first/);
  });

  it("says so rather than understating the surface when it hits an export star", () => {
    const dir = pkgFixture({ released: ["fetchSearch"], built: ["fetchSearch"] });
    write(dir, "dist/index.d.ts", 'export { fetchSearch } from "./x";\nexport * from "./y";\n');

    const { code, out } = run(dir);

    strictEqual(code, 1, out);
    match(out, /export \* from/);
  });
});

describe("check-exports: packages that have not adopted it", () => {
  it("is a no-op without an exportsSurface block", () => {
    const root = mkdtempSync(join(tmpdir(), "turing-exports-"));
    roots.push(root);
    write(root, "package.json", '{ "name": "turing-app", "version": "1.0.0" }\n');

    const { code, out } = run(root);

    strictEqual(code, 0, out);
    match(out, /declares no "exportsSurface"/);
  });
});
