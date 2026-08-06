import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * T892 / §LXIX.7 — the gate for every Java enum this SDK re-declares as a
 * TypeScript string union.
 *
 * `MISSING_DEFAULT_LLM` was added to `ChatDisabledReason` on the Java side and
 * left out of the mirror for eight months, narrowing the reason to `undefined`
 * in every consumer. Nothing could have caught it: a `Record<Union, string>` is
 * exhaustive only against the union, never against the enum it mirrors. The one
 * thing that catches it is reading both declarations and asserting set equality,
 * so this takes a pair list — a new mirrored enum is a row here, not a new test.
 */

/** Repo root, four levels up from `frontend/packages/sdk/src/__tests__`. */
const REPO = fileURLToPath(new URL("../../../../../", import.meta.url));

interface MirrorPair {
  /** What the pair is called in prose, for the test name. */
  readonly name: string;
  /** Java source, repo-relative. */
  readonly javaFile: string;
  /** The `enum <name>` to read out of it — a file may declare more than one. */
  readonly javaEnum: string;
  /** TypeScript source, repo-relative. */
  readonly tsFile: string;
  /** The `export type <name> =` to read out of it. */
  readonly tsType: string;
}

const PAIRS: readonly MirrorPair[] = [
  {
    name: "ChatDisabledReason",
    javaFile: "turing-app/src/main/java/com/viglet/turing/api/sn/genai/TurSNSiteGenAiAPI.java",
    javaEnum: "ChatDisabledReason",
    tsFile: "frontend/packages/sdk/src/api.ts",
    tsType: "ChatDisabledReason",
  },
  {
    name: "TurAnswerSignal",
    javaFile: "turing-app/src/main/java/com/viglet/turing/genai/answer/TurAnswerSignal.java",
    javaEnum: "TurAnswerSignal",
    tsFile: "frontend/packages/sdk/src/api.ts",
    tsType: "TurAnswerSignalKind",
  },
  {
    name: "TurSNFacetItemAction",
    javaFile:
      "turing-commons/src/main/java/com/viglet/turing/commons/sn/search/TurSNFacetItemAction.java",
    javaEnum: "TurSNFacetItemAction",
    tsFile: "frontend/packages/sdk/src/types.ts",
    tsType: "TurFacetItemAction",
  },
  {
    name: "TurSNResponseFormat",
    javaFile:
      "turing-commons/src/main/java/com/viglet/turing/commons/sn/search/TurSNResponseFormat.java",
    javaEnum: "TurSNResponseFormat",
    tsFile: "frontend/packages/sdk/src/types.ts",
    tsType: "TurResponseFormat",
  },
];

/** Drops `//` and `/* *\/` comments so a commented-out constant never counts. */
function stripComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/[^\n]*/g, "");
}

/**
 * The constants declared by `enum <name>` in a Java file. The constant list runs
 * from the opening brace to the first `;` (which ends it when the enum has
 * members) or to the closing brace (when it has none). Constructor arguments and
 * bodies are dropped, so `THUMBS_DOWN(true)` reads as `THUMBS_DOWN`.
 */
function javaEnumConstants(source: string, enumName: string): string[] {
  const declaration = new RegExp(`\\benum\\s+${enumName}\\b[^{]*\\{`).exec(
    stripComments(source),
  );
  if (!declaration) {
    throw new Error(`no 'enum ${enumName}' declaration found`);
  }

  const body = stripComments(source).slice(
    declaration.index + declaration[0].length,
  );

  // Walk to the end of the constant list, tracking nesting so a constant body
  // (`FOO { ... }`) or an argument list cannot end it early.
  let depth = 0;
  let end = body.length;
  for (let i = 0; i < body.length; i++) {
    const c = body[i];
    if (c === "{" || c === "(") depth++;
    else if (c === ")") depth--;
    else if (c === "}") {
      if (depth === 0) {
        end = i;
        break;
      }
      depth--;
    } else if (c === ";" && depth === 0) {
      end = i;
      break;
    }
  }

  return body
    .slice(0, end)
    .split(",")
    .map((entry) => /^\s*([A-Z][A-Z0-9_]*)/.exec(entry)?.[1])
    .filter((name): name is string => Boolean(name));
}

/**
 * The string literals of `export type <name> = "A" | "B";`. Comments are dropped
 * first, so the doc block each member may carry cannot contribute a literal.
 */
function tsUnionMembers(source: string, typeName: string): string[] {
  const declaration = new RegExp(
    `export type ${typeName}\\s*=([\\s\\S]*?);`,
  ).exec(stripComments(source));
  if (!declaration) {
    throw new Error(`no 'export type ${typeName}' declaration found`);
  }
  return [...declaration[1].matchAll(/"([^"]+)"/g)].map((m) => m[1]);
}

function read(relative: string): string {
  return readFileSync(new URL(relative, `file://${REPO.replaceAll("\\", "/")}`), "utf8");
}

describe("Java enum ↔ TypeScript union mirrors", () => {
  it.each(PAIRS.map((pair) => [pair.name, pair] as const))(
    "%s declares the same constants on both sides",
    (_name, pair) => {
      const javaConstants = javaEnumConstants(read(pair.javaFile), pair.javaEnum);
      const tsMembers = tsUnionMembers(read(pair.tsFile), pair.tsType);

      expect(javaConstants.length).toBeGreaterThan(0);
      expect([...tsMembers].sort()).toEqual([...javaConstants].sort());
    },
  );

  it("finds a constant the parser could otherwise have swallowed", () => {
    // Guards the parser itself: a constant with arguments, one with a body, a
    // commented-out one, and members after the `;` must all be handled.
    const source = `
      public enum Sample {
        /** doc */
        ALPHA(true),
        // GAMMA,
        BETA(false) { public void run() {} },
        DELTA;
        private final boolean flag;
      }
    `;
    expect(javaEnumConstants(source, "Sample")).toEqual(["ALPHA", "BETA", "DELTA"]);
  });
});
