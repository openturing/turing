import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * T914 / §LXIX.10 — the publish step must be `pnpm publish`, never `npm publish`.
 *
 * These are pnpm workspaces. `npm publish` copies a `workspace:` specifier into
 * the published manifest verbatim, because npm has no workspace of its own to
 * resolve it against — so `@viglet/turing-react-sdk` would have shipped
 * `"@viglet/turing-sdk": "workspace:*"`, which no registry client can install.
 *
 * The four other packages declare no workspace dependency today, which is the
 * only reason their workflows were not broken too. That is a fact about their
 * current dependency lists and not a property anything enforces, so the rule is
 * checked across every workflow rather than on the one package that hit it.
 */

const REPO = fileURLToPath(new URL("../../../../../", import.meta.url));
const WORKFLOWS = "\\.github/workflows";

function read(relative: string): string {
  return readFileSync(new URL(relative, `file://${REPO.replaceAll("\\", "/")}`), "utf8");
}

function workflowFiles(): string[] {
  return readdirSync(new URL(".github/workflows/", `file://${REPO.replaceAll("\\", "/")}`))
    .filter((name) => name.endsWith(".yml") || name.endsWith(".yaml"))
    .sort();
}

/** Every `run:` command in a workflow, one per line, comments dropped. */
function runCommands(source: string): string[] {
  return source
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => !line.startsWith("#"))
    .filter((line) => line.length > 0);
}

describe("npm publish workflows", () => {
  const files = workflowFiles();

  it("finds the workflow directory", () => {
    expect(files.length).toBeGreaterThan(0);
  });

  it.each(files)("%s never calls bare `npm publish`", (name) => {
    const offenders = runCommands(read(`.github/workflows/${name}`)).filter((line) =>
      /(^|[|&;\s])npm\s+publish\b/.test(line),
    );
    expect(offenders, `${WORKFLOWS}/${name} must use \`pnpm publish\``).toEqual([]);
  });

  it("every publishing workflow passes --no-git-checks", () => {
    // actions/checkout leaves a detached HEAD, and the react-sdk workflow bumps
    // the version before publishing — pnpm refuses both without this flag, so a
    // `pnpm publish` added without it fails only when someone dispatches it.
    const missing = files.filter((name) => {
      const commands = runCommands(read(`.github/workflows/${name}`));
      return commands.some(
        (line) => /\bpnpm\s+publish\b/.test(line) && !line.includes("--no-git-checks"),
      );
    });
    expect(missing).toEqual([]);
  });

  it("the packages that publish are the ones with a publish workflow", () => {
    const publishing = files.filter((name) =>
      runCommands(read(`.github/workflows/${name}`)).some((line) => /\bpnpm\s+publish\b/.test(line)),
    );
    // One per publishable package: react-sdk, react-ui, sdk, cli, flow-dsl.
    expect(publishing).toEqual([
      "publish-cli.yml",
      "publish-flow-dsl.yml",
      "publish-js-sdk.yml",
      "publish-react-ui.yml",
      "publish-sdk.yml",
    ]);
  });
});
