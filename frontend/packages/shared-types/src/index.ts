/**
 * Shared TypeScript types for the Viglet Turing ES ecosystem.
 *
 * Contains interfaces that are consumed by both the React SDK
 * (@viglet/turing-react-sdk) and the apps that depend on it, so there is a
 * single source of truth for the Turing API surface.
 *
 * Add types here when they describe Turing API request/response shapes or
 * domain objects crossed between the SDK and its consumers. Keep app-local
 * types inside each app's own `src/models/` folder.
 */

/** Shape returned by the `/api/sn/{site}/search` endpoint for a single hit. */
export interface TurSearchHit {
  turSEResultAttr: Record<string, unknown>;
}

/** Pagination/meta envelope returned for any search call. */
export interface TurSearchQueryContext {
  count: number;
  index: number;
  limit: number;
  pageCount: number;
  currentPage: number;
}

/** Top-level payload returned by the search endpoint. */
export interface TurSearchResponse {
  queryContext: TurSearchQueryContext;
  results: {
    document: TurSearchHit[];
  };
}

/** Generic feature flag envelope exposed by `/api/features`. */
export interface TurFeatures {
  storageEnabled: boolean;
  ragEnabled: boolean;
  gitServerEnabled: boolean;
  marketplaceEnabled: boolean;
  loggingEngine: "none" | "mongodb" | "redis";
}
