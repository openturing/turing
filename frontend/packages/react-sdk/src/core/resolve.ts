import type { ResolvedDocument, ResolvedGroup, TurSearchResponse } from "./types";

/**
 * Maps raw API documents to ResolvedDocument using the default field mappings
 * from the query context. Shared by provider and standalone hooks.
 */
export function resolveDocuments(response: TurSearchResponse): ResolvedDocument[] {
  // Degrade gracefully when the backend returns no query context (e.g. the Solr
  // query failed): show an empty result instead of throwing a TypeError.
  const df = response?.queryContext?.defaultFields;
  if (!df) return [];
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

/**
 * Maps raw API groups to ResolvedGroup with resolved documents.
 */
export function resolveGroups(response: TurSearchResponse): ResolvedGroup[] {
  const df = response?.queryContext?.defaultFields;
  if (!response.groups || !df) return [];
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
