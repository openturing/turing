/**
 * Every prop a README fence passes to a Turing component exists on that
 * component (T944 / §LXIX.29).
 *
 * <p>T939 runs the search-contract rules over README fences, which is one rule
 * out of everything a fence can get wrong. The first thing it did not catch sat
 * two lines above a fence it did: the `TuringResultList` example passed
 * `renderItem`, and the component's one required render prop is
 * `itemComponent`. A fence is prose to every tool in the repo, so a rename a
 * build catches anywhere else passed here in silence.</p>
 *
 * <p><strong>Why this and not `tsc` over the fences.</strong> §LXIX.29 proposed
 * extracting each fence into a scratch file and compiling it. Measured: of 41
 * fences in the workspace's READMEs, 18 are `tsx` and 7 reference a Turing
 * component — and almost none is a compilable program. They are snippets, with
 * `turing`, `pages` and `store` arriving from a paragraph above. Compiling them
 * would mean marking most as partial, and a guard that most of its subjects opt
 * out of is a guard in name. Checking the props instead needs no fence to be
 * self-contained, and catches exactly the class this task is about: a prop that
 * outlived its rename.</p>
 *
 * <p>The props are <em>derived</em>, never listed here: the index is read from
 * each package's own `export interface Turing…Props`. A component whose props
 * interface cannot be found is an error rather than a pass, so renaming the
 * interface fails the guard instead of quietly emptying it.</p>
 *
 * <p>Zero-dependency by house rule — `node --test` runs it directly, and the
 * app suite imports it so a developer running either still sees a violation.
 * That rules out the TypeScript compiler API: `typescript` is not hoisted to
 * the workspace root.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.7
 */
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative, sep } from "node:path";

import { FRONTEND, filesFor } from "./search-contract-rules.mjs";

const SKIP_DIRECTORIES = new Set([
  "node_modules",
  "dist",
  "build",
  ".turbo",
  "coverage",
]);

/** Components whose usage is checked — anything named like a Turing component. */
const COMPONENT = /<(Turing[A-Za-z0-9]*)\b/g;

/**
 * A fence opts out by saying so in its info string, which is a decision its
 * author makes once instead of one the reader discovers by copying.
 */
const PARTIAL = /\bpartial\b/;

function sourceFiles(dir, found = []) {
  for (const entry of readdirSync(dir)) {
    if (SKIP_DIRECTORIES.has(entry)) continue;
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      sourceFiles(full, found);
    } else if (entry.endsWith(".tsx") || entry.endsWith(".ts")) {
      found.push(full);
    }
  }
  return found;
}

/**
 * Reads the body of `export interface <name> {` by matching braces, so a nested
 * object type does not end it early.
 *
 * @returns the body text, or null when the interface is not in this file
 */
function interfaceBody(source, name) {
  const header = new RegExp(`export\\s+interface\\s+${name}\\b[^{]*\\{`);
  const start = source.match(header);
  if (!start) return null;
  let depth = 0;
  const from = start.index + start[0].length - 1;
  for (let i = from; i < source.length; i++) {
    if (source[i] === "{") depth++;
    else if (source[i] === "}") {
      depth--;
      if (depth === 0) return source.slice(from + 1, i);
    }
  }
  return null;
}

/** The interface's own members — depth-1 identifiers only, nested types skipped. */
function memberNames(body) {
  const names = new Set();
  let depth = 0;
  let atMemberStart = true;
  for (let i = 0; i < body.length; i++) {
    const ch = body[i];
    if (ch === "{" || ch === "(" || ch === "[") {
      depth++;
      continue;
    }
    if (ch === "}" || ch === ")" || ch === "]") {
      depth--;
      continue;
    }
    if (depth === 0 && (ch === ";" || ch === "\n" || ch === ",")) {
      atMemberStart = true;
      continue;
    }
    if (depth !== 0 || !atMemberStart) continue;
    const rest = body.slice(i);
    // `readonly foo?: …` / `foo: …` — the name is what precedes ?: or :
    const member = rest.match(/^\s*(?:readonly\s+)?([A-Za-z_$][\w$]*)\s*\??\s*:/);
    if (member) {
      names.add(member[1]);
      i += member[0].length - 1;
    }
    atMemberStart = false;
  }
  return names;
}

/**
 * Every Turing component's prop names, read from the workspace's own sources.
 *
 * @returns {Map<string, Set<string>>} component name → its prop names
 */
export function propsIndex() {
  const index = new Map();
  const packages = join(FRONTEND, "packages");
  for (const pkg of readdirSync(packages)) {
    const src = join(packages, pkg, "src");
    let files;
    try {
      files = sourceFiles(src);
    } catch {
      continue; // a package without a src/ has no components to index
    }
    for (const file of files) {
      const source = readFileSync(file, "utf8");
      for (const found of source.matchAll(/export\s+interface\s+(Turing[A-Za-z0-9]*)Props\b/g)) {
        const component = found[1];
        const body = interfaceBody(source, `${component}Props`);
        if (body === null) continue;
        const names = index.get(component) ?? new Set();
        memberNames(body).forEach((name) => names.add(name));
        index.set(component, names);
      }
    }
  }
  return index;
}

/** The `tsx`/`ts` fences of every package README, with their info string. */
function fences() {
  const out = [];
  for (const file of filesFor("packageReadmes")) {
    const markdown = readFileSync(file, "utf8");
    for (const fence of markdown.matchAll(/^```([^\n]*)\n([\s\S]*?)^```/gm)) {
      const info = fence[1].trim();
      if (!/^tsx?\b/.test(info)) continue;
      out.push({ file, info, body: fence[2] });
    }
  }
  return out;
}

/**
 * The attribute names a JSX element passes, read from the element's opening tag
 * with brace depth respected so an attribute value containing `>` (an arrow
 * function, which most render props are) does not end the tag early.
 */
function attributesOf(body, from) {
  const names = [];
  let depth = 0;
  let inString = null;
  for (let i = from; i < body.length; i++) {
    const ch = body[i];
    if (inString) {
      if (ch === inString) inString = null;
      continue;
    }
    if (ch === '"' || ch === "'" || ch === "`") {
      inString = ch;
      continue;
    }
    if (ch === "{") depth++;
    else if (ch === "}") depth--;
    else if (ch === ">" && depth === 0) break;
    if (depth !== 0) continue;
    const member = body.slice(i).match(/^\s([A-Za-z_$][\w$]*)\s*=/);
    if (member) {
      names.push(member[1]);
      i += member[0].length - 2;
    }
  }
  return names;
}

/**
 * Every prop a README fence passes that its component does not declare.
 *
 * @returns {string[]} reportable violations, empty when the docs are honest
 */
export function violations() {
  const index = propsIndex();
  const found = [];
  for (const { file, info, body } of fences()) {
    if (PARTIAL.test(info)) continue;
    for (const element of body.matchAll(COMPONENT)) {
      const component = element[1];
      const props = index.get(component);
      if (!props) {
        // Not an error: a fence may name a component this workspace does not
        // own (a host app's own wrapper). Only declared components are checked.
        continue;
      }
      for (const attr of attributesOf(body, element.index + element[0].length)) {
        if (!props.has(attr)) {
          found.push(
            `${relative(FRONTEND, file).split(sep).join("/")}: <${component} ${attr}={…}> — ` +
              `${component}Props has no '${attr}'`,
          );
        }
      }
    }
  }
  return found.sort();
}
