/**
 * T1024 — the console-to-bento path mapping, on its own.
 *
 * Pure: a string in, a string out. It lived beside the component that navigates
 * with it, and a module exporting both a component and a function reloads whole
 * instead of preserving state. Its own test imports it directly, which is the
 * other reason it reads better as a module than as a neighbour.
 */
import { ROUTES } from "../routes.const";

/**
 * Console → Bento deep-link redirect (Block AG / T569).
 *
 * After the cutover, `/bento` is the interface and the legacy console tree is
 * gone. Old bookmarks and external deep-links into the console context path
 * (`/admin/...`) must still resolve, so a single splat route mounts this
 * component and rewrites the path to its bento equivalent.
 *
 * Most console surfaces mirror their bento sub-path 1:1 under `/bento` (only the
 * `/admin` → `/bento` prefix changes). The map below lists the handful of
 * surfaces whose bento path differs (the flattened `admin-settings/*` tree, the
 * `embedding-model` → `embedding` rename). Longest prefix wins; anything not
 * listed maps straight through.
 */
const SEGMENT_REMAPS: ReadonlyArray<readonly [string, string]> = [
  ["admin-settings/users", "admin/users"],
  ["admin-settings/groups", "admin/groups"],
  ["admin-settings/roles", "admin/roles"],
  ["admin-settings/tokens", "token/instance"],
  ["admin-settings/settings", "global-settings"],
  ["admin-settings/genai", "global-settings"],
  ["admin-settings/system-info", "system-info"],
  ["admin-settings/import", "import"],
  ["admin-settings/logging", "logging"],
  ["admin-settings", "admin"],
  ["embedding-model", "embedding"],
];

/** Map a console path suffix (no context path, no leading slash) to a bento path. */
export function consoleSuffixToBento(consoleSuffix: string): string {
  if (!consoleSuffix || consoleSuffix === "home") return ROUTES.BENTO_HOME;
  for (const [from, to] of SEGMENT_REMAPS) {
    if (consoleSuffix === from || consoleSuffix.startsWith(`${from}/`)) {
      return `${ROUTES.BENTO_ROOT}/${to}${consoleSuffix.slice(from.length)}`;
    }
  }
  return `${ROUTES.BENTO_ROOT}/${consoleSuffix}`;
}
