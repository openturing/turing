import type { ResolvedDocument, ResolvedGroup, TurSearchResponse } from "./types";

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
export function resolveDocuments(response: TurSearchResponse): ResolvedDocument[] {
  const df = response.queryContext.defaultFields;
  const docs = response.results?.document ??
    response.groups?.flatMap((g) => g.results?.document ?? []) ?? [];
  return docs.map((doc) => ({
    url: doc.fields[df.url] ?? "",
    title: doc.fields[df.title] ?? "",
    description: doc.fields[df.description] ?? "",
    date: doc.fields[df.date] ?? "",
    image: doc.fields[df.image] ?? "",
    text: doc.fields[df.text] ?? "",
    raw: doc,
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
    documents: (g.results?.document ?? []).map((doc) => ({
      url: doc.fields[df.url] ?? "",
      title: doc.fields[df.title] ?? "",
      description: doc.fields[df.description] ?? "",
      date: doc.fields[df.date] ?? "",
      image: doc.fields[df.image] ?? "",
      text: doc.fields[df.text] ?? "",
      raw: doc,
    })),
  }));
}
