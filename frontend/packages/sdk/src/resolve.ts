import type {
  ResolvedDocument,
  ResolvedGroup,
  TurDefaultFields,
  TurDocument,
  TurQueryContext,
  TurSearchResponse,
  TurStructuredSearchResponse,
} from "./types";

/**
 * T709 — the shape both response formats share. `resolveDocuments` only ever
 * reads the default-field map and the documents, and those are identical in the
 * legacy and the structured (link-free) responses — so one resolver serves both
 * instead of a near-duplicate per format.
 *
 * @since 2026.3.4
 */
export interface ResolvableSearchResponse {
  queryContext: TurQueryContext;
  results?: { document: TurDocument[] };
  groups?: readonly { results?: { document: TurDocument[] } }[];
}

/**
 * Maps raw API documents/groups to the resolved shapes using the default
 * field mappings from the query context. Ported verbatim from the React SDK's
 * `core/resolve.ts` — pure functions, no framework dependency.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */

/**
 * Maps raw API documents to ResolvedDocument using the default field mappings
 * from the query context.
 */
export function resolveDocuments(response: ResolvableSearchResponse): ResolvedDocument[] {
  const df = response.queryContext.defaultFields;
  const docs = response.results?.document ??
    response.groups?.flatMap((g) => g.results?.document ?? []) ?? [];
  return docs.map((doc) => resolveDocument(doc, df));
}

/** Shared field projection, so the two group resolvers cannot drift. */
function resolveDocument(doc: TurDocument, df: TurDefaultFields): ResolvedDocument {
  return {
    url: doc.fields[df.url] ?? "",
    title: doc.fields[df.title] ?? "",
    description: doc.fields[df.description] ?? "",
    date: doc.fields[df.date] ?? "",
    image: doc.fields[df.image] ?? "",
    text: doc.fields[df.text] ?? "",
    raw: doc,
  };
}

/**
 * T709 — maps the link-free response's groups. Same projection as
 * {@link resolveGroups}, but the pagination lands on `structuredPagination`
 * (page numbers) instead of `pagination` (hrefs, which the structured response
 * deliberately does not carry).
 *
 * @since 2026.3.4
 */
export function resolveStructuredGroups(
  response: TurStructuredSearchResponse,
): ResolvedGroup[] {
  if (!response.groups) return [];
  const df = response.queryContext.defaultFields;
  return response.groups.map((g) => ({
    name: g.name,
    count: g.count,
    page: g.page,
    pageCount: g.pageCount,
    limit: g.limit,
    pagination: [],
    structuredPagination: g.pagination ?? [],
    documents: (g.results?.document ?? []).map((doc) => resolveDocument(doc, df)),
  }));
}

/** Maps raw API groups to ResolvedGroup with resolved documents. */
export function resolveGroups(response: TurSearchResponse): ResolvedGroup[] {
  if (!response.groups) return [];
  const df = response.queryContext.defaultFields;
  return response.groups.map((g) => ({
    name: g.name,
    count: g.count,
    page: g.page,
    pageCount: g.pageCount,
    limit: g.limit,
    pagination: g.pagination ?? [],
    documents: (g.results?.document ?? []).map((doc) => resolveDocument(doc, df)),
  }));
}
