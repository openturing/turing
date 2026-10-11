import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { describe, expect, it } from "vitest";
import * as vanillaSdk from "../index";

/**
 * T406 — SDK parity guard.
 *
 * The root cause Block S exists to fix: nothing failed when a new public
 * (client-facing) REST/SSE endpoint shipped without an SDK wrapper, so the two
 * JS SDKs silently drifted behind the backend (T384 / slot-upload / resume /
 * spell-check all accumulated this way).
 *
 * This contract test is the single source of truth for the public client
 * surface. Every entry asserts that BOTH SDKs expose a wrapper:
 *
 *  - the vanilla `@viglet/turing-sdk` — checked at runtime (the named export
 *    resolves to a function), and
 *  - the React `@viglet/turing-react-sdk` — checked by source text (its barrel
 *    re-exports the name and `core/api.ts` defines it). The React SDK is read as
 *    text rather than imported so this test stays a zero-dependency Node test
 *    (no axios / React / jsdom needed in the vanilla package).
 *
 * **Convention:** when you ship a new public endpoint, add it to
 * {@link PUBLIC_ENDPOINTS}. CI then fails until both SDKs export a wrapper —
 * "ship a public endpoint" now includes "ship its SDK wrapper" by construction.
 * The admin / console-only surface is deliberately out of scope.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

interface EndpointContract {
  readonly method: string;
  /** Server path (after `/api`), for documentation/review. */
  readonly path: string;
  /** Exported wrapper name — identical in both SDKs by convention. */
  readonly fn: string;
}

const PUBLIC_ENDPOINTS: readonly EndpointContract[] = [
  // ── Search ──
  { method: "GET", path: "/sn/{site}/search", fn: "fetchSearch" },
  { method: "POST", path: "/sn/{site}/search/query", fn: "postSearch" }, // T707/T711
  { method: "POST", path: "/sn/{site}/click", fn: "postClick" },
  { method: "GET", path: "/sn/{site}/ac", fn: "fetchAutoComplete" },
  { method: "GET", path: "/sn/{site}/search/sort-options", fn: "fetchSortOptions" },
  { method: "GET", path: "/sn/{site}/search/similar", fn: "fetchSimilar" }, // T400
  { method: "GET", path: "/sn/{site}/search/document", fn: "fetchStoredDocuments" }, // T1077
  { method: "GET", path: "/sn/{site}/{locale}/spell-check", fn: "fetchSpellCheck" }, // T402
  { method: "GET", path: "/sn/{site}/{locale}/related-terms", fn: "fetchRelatedTerms" }, // T678
  { method: "POST", path: "/sn/{site}/_search", fn: "dslSearch" }, // T403
  { method: "POST", path: "/sn/{site}/query", fn: "dslQuery" }, // T403
  // ── Chat (site / agent / llm) ──
  { method: "GET", path: "/sn/{site}/chat", fn: "fetchChat" },
  { method: "GET", path: "/sn/{site}/chat/enabled", fn: "fetchChatEnabled" },
  { method: "POST", path: "/sn/{site}/chat/conversation", fn: "postChatConversation" },
  { method: "POST", path: "/v2/ai-agent/{agentId}/chat", fn: "postAgentChat" },
  { method: "POST", path: "/v2/persona/{personaId}/chat", fn: "postPersonaChat" }, // T579

  { method: "POST", path: "/v2/llm/{id}/chat", fn: "postLlmChat" },
  { method: "POST", path: "/v2/llm/{id}/semantic-chat", fn: "postSemanticChat" }, // T404
  { method: "DELETE", path: "/sn/{site}/chat/conversation-state", fn: "deleteSiteConversationState" },
  { method: "DELETE", path: "/v2/ai-agent/{agentId}/chat-flow-state", fn: "deleteAgentFlowState" },
  { method: "GET", path: "/sn/{site}/chat/intents", fn: "fetchIntents" },
  { method: "GET", path: "/sn/{site}/chat/state", fn: "fetchSiteChatState" },
  { method: "POST", path: "/sn/{site}/chat/handoff", fn: "postSiteHandoff" },
  { method: "POST", path: "/sn/{site}/chat/flow-select", fn: "postSiteFlowSelect" },
  { method: "POST", path: "/sn/{site}/persona/{personaId}/content-fit", fn: "fetchPersonaContentFit" }, // T635

  { method: "POST", path: "/sn/{site}/chat/form-submit", fn: "postSiteFormSubmit" },
  { method: "POST", path: "/sn/{site}/chat/resume", fn: "postSiteChatResume" }, // T401
  // ── Slots ──
  { method: "GET", path: "/sn/{site}/chat/slots", fn: "fetchSiteChatSlots" },
  { method: "GET", path: "/v2/ai-agent/{agentId}/chat-slots", fn: "fetchAgentChatSlots" },
  { method: "POST", path: "/sn/{site}/chat/slots", fn: "postSiteChatSlot" },
  { method: "POST", path: "/sn/{site}/chat/slot-extract", fn: "postSiteSlotExtract" },
  { method: "POST", path: "/sn/{site}/chat/slot-extract-multi", fn: "postSiteSlotExtractMulti" }, // T101
  { method: "POST", path: "/sn/{site}/chat/slot-upload", fn: "postSiteSlotUpload" }, // T401
  // ── LLM / platform discovery ──
  { method: "GET", path: "/llm", fn: "fetchLlmInstances" },
  { method: "GET", path: "/v2/llm/{id}/chat/context-info", fn: "fetchLlmContextInfo" },
  { method: "GET", path: "/v2/ai-agent/{agentId}/chat/context-info", fn: "fetchAgentContextInfo" },
  { method: "GET", path: "/discovery", fn: "fetchDiscovery" }, // T405
  { method: "GET", path: "/features", fn: "fetchFeatures" }, // T405
  { method: "GET", path: "/locale", fn: "fetchSystemLocales" }, // T405
  { method: "GET", path: "/llm/vendor", fn: "fetchLlmVendors" }, // T405
  { method: "POST", path: "/v2/summary", fn: "postSummary" }, // T405
];

const here = dirname(fileURLToPath(import.meta.url));
const reactSdkRoot = resolve(here, "../../../react-sdk/src");
const reactIndex = readFileSync(resolve(reactSdkRoot, "index.ts"), "utf8");
const reactCoreApi = readFileSync(resolve(reactSdkRoot, "core/api.ts"), "utf8");
const reactResolve = readFileSync(resolve(reactSdkRoot, "core/resolve.ts"), "utf8");
const reactTypes = readFileSync(resolve(reactSdkRoot, "core/types.ts"), "utf8");

/** True when `name` appears inside an `export { … } from "@viglet/turing-sdk"` block. */
function reExportsFromVanillaSdk(source: string, name: string): boolean {
  const blocks = source.match(/export\s*\{[^}]*\}\s*from\s*"@viglet\/turing-sdk"/g) ?? [];
  return blocks.some((block) => new RegExp(`\\b${name}\\b`).test(block));
}

/**
 * Matches `export function foo` / `export async function foo` in core/api.ts,
 * **or** a re-export of that name from the vanilla SDK.
 *
 * T711 — the re-export form counts on purpose. Some wrappers are pure functions
 * with no React or axios coupling (`applyFacetAction`, the `SearchParams` <->
 * `TurSearchRequest` bridges), and for those the React SDK re-exports the
 * vanilla implementation instead of copying it. A copy is precisely the drift
 * this guard exists to prevent, so a copy must not be the only accepted shape.
 */
function reactDefinesFn(name: string): boolean {
  if (new RegExp(`export (async )?function ${name}\\b`).test(reactCoreApi)) return true;
  return (
    reExportsFromVanillaSdk(reactCoreApi, name) ||
    reExportsFromVanillaSdk(reactResolve, name)
  );
}

/** Matches the name appearing in the React barrel's export list. */
function reactBarrelExports(name: string): boolean {
  return new RegExp(`\\b${name}\\b`).test(reactIndex);
}

describe("SDK parity guard (T406)", () => {
  it.each(PUBLIC_ENDPOINTS)(
    "vanilla SDK wraps $method $path as $fn()",
    ({ fn }) => {
      expect(
        typeof (vanillaSdk as Record<string, unknown>)[fn],
        `@viglet/turing-sdk is missing a wrapper named "${fn}" for this public endpoint`,
      ).toBe("function");
    },
  );

  it.each(PUBLIC_ENDPOINTS)(
    "React SDK wraps $method $path as $fn()",
    ({ fn }) => {
      expect(
        reactDefinesFn(fn),
        `@viglet/turing-react-sdk core/api.ts is missing "export function ${fn}" for this public endpoint`,
      ).toBe(true);
      expect(
        reactBarrelExports(fn),
        `@viglet/turing-react-sdk index.ts does not re-export "${fn}"`,
      ).toBe(true);
    },
  );

  it("has no duplicate wrapper names in the contract", () => {
    const names = PUBLIC_ENDPOINTS.map((e) => e.fn);
    expect(new Set(names).size).toBe(names.length);
  });
});

/**
 * T711 / §XLV — parity guard for the POST-native, link-free search path.
 *
 * The endpoint wrapper alone (`postSearch`, above) is not the whole contract
 * here: the value of Block AV is that a client can rebuild its next request
 * from *data*. That takes a specific set of helpers and a specific response
 * shape, and if either SDK loses one, that SDK silently falls back to parsing
 * hrefs — the exact regression §XLV closes.
 */
describe("POST-native structured contract parity (T711)", () => {
  /** Helpers a POST-native client needs beyond the endpoint wrapper itself. */
  const STRUCTURED_HELPERS = [
    "postStructuredSearch",
    "applyFacetAction",
    "searchParamsToRequest",
    "requestToSearchParams",
    "resolveStructuredGroups",
  ] as const;

  /** The response/request shape both SDKs must publish. */
  const STRUCTURED_TYPES = [
    "TurSearchRequest",
    "TurResponseFormat",
    "TurFacetItemAction",
    "TurStructuredSearchResponse",
    "TurStructuredFacet",
    "TurStructuredFacetItem",
    "TurStructuredPage",
    "TurStructuredLocale",
    "TurStructuredSpellCheck",
    "TurStructuredGroup",
  ] as const;

  it.each(STRUCTURED_HELPERS)("vanilla SDK exports %s()", (fn) => {
    expect(
      typeof (vanillaSdk as Record<string, unknown>)[fn],
      `@viglet/turing-sdk is missing "${fn}" — the POST-native path needs it`,
    ).toBe("function");
  });

  it.each(STRUCTURED_HELPERS)("React SDK exports %s()", (fn) => {
    expect(
      reactDefinesFn(fn),
      `@viglet/turing-react-sdk neither defines nor re-exports "${fn}"`,
    ).toBe(true);
    expect(
      reactBarrelExports(fn),
      `@viglet/turing-react-sdk index.ts does not re-export "${fn}"`,
    ).toBe(true);
  });

  it.each(STRUCTURED_TYPES)("both SDKs publish the %s type", (typeName) => {
    expect(
      new RegExp(`\\b${typeName}\\b`).test(reactTypes),
      `@viglet/turing-react-sdk core/types.ts does not re-export "${typeName}"`,
    ).toBe(true);
    expect(
      reactBarrelExports(typeName),
      `@viglet/turing-react-sdk index.ts does not re-export "${typeName}"`,
    ).toBe(true);
  });

  /**
   * The demo (`turing-demo.viglet.org`) is anonymous and its nginx allowlist
   * proxies `/api/sn/` method-agnostically, so POST search reaches the backend.
   * The server also lists `/api/sn/**` as CSRF-exempt (T707), which is why the
   * vanilla client sends the search POST with `csrf: false`. If that ever flips
   * back to priming, every public embed pays an extra `/csrf` round-trip per
   * search — so pin it here, next to the contract it belongs to.
   */
  it("sends the search POST without CSRF priming", async () => {
    const calls: unknown[][] = [];
    const client = {
      post: (...args: unknown[]) => {
        calls.push(args);
        return Promise.resolve({});
      },
    } as unknown as Parameters<typeof vanillaSdk.postSearch>[0];

    await vanillaSdk.postSearch(client, "wknd", { q: "*" });

    expect(calls[0][0]).toBe("/sn/wknd/search/query");
    expect(calls[0][2]).toMatchObject({ csrf: false });
  });
});
