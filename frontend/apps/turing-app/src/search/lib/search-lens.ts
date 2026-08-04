/**
 * T855 / §LXIV.4 (Block BQ) — the three search lenses and the translation
 * between their URL dialects. A wrong answer has three possible causes:
 * retrieval never found the document (**recall**), it was found but ranked away
 * (**rerank**), or the evidence was right and the synthesis wasn't. Replaying one
 * question across `/sn`, `/ann` and `/answer` attributes it in a single pass —
 * which only works if the question, the locale and the active filters survive
 * the jump.
 *
 * <p>The dialects differ: `/sn` and `/answer` both carry filters as repeated
 * Solr-style `fq=field:value`, while `/ann` carries them as bare repeated
 * `field=value` params. These pure functions are the single place that knows it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

export type SearchLens = "keyword" | "vector" | "answer";

/** The lens-independent state of one question. */
export interface LensQuery {
  q: string;
  locale?: string;
  /** Active filters as `field → values`. */
  filters: Record<string, string[]>;
}

/** Route prefix of each lens. Mirrors `ROUTES.SN_SEARCH` / `ANN_SEARCH` / `ANSWER_SEARCH`. */
const LENS_PREFIX: Readonly<Record<SearchLens, string>> = {
  keyword: "/sn",
  vector: "/ann",
  answer: "/answer",
};

/**
 * Params `/ann` owns, so everything else in its URL is a metadata filter. The
 * paging/topK trio is deliberately NOT carried across lenses: it is a property
 * of one lens's result list, not of the question.
 */
const VECTOR_RESERVED = new Set(["q", "_setlocale", "topK", "page", "pageSize"]);

/** Params the `fq`-dialect lenses own. */
const FQ_RESERVED = new Set(["q", "_setlocale", "fq"]);

/** True when the lens spells filters as repeated `fq=field:value`. */
function usesFqDialect(lens: SearchLens): boolean {
  return lens !== "vector";
}

function addFilter(filters: Record<string, string[]>, field: string, value: string): void {
  if (!field || !value) return;
  filters[field] ??= [];
  filters[field].push(value);
}

/** Reads one lens's URL into the lens-independent question. */
export function readLensQuery(lens: SearchLens, params: URLSearchParams): LensQuery {
  const filters: Record<string, string[]> = {};
  if (usesFqDialect(lens)) {
    for (const raw of params.getAll("fq")) {
      const separator = raw.indexOf(":");
      if (separator <= 0) continue;
      addFilter(filters, raw.slice(0, separator), raw.slice(separator + 1));
    }
  } else {
    for (const [key, value] of params.entries()) {
      if (VECTOR_RESERVED.has(key)) continue;
      addFilter(filters, key, value);
    }
  }
  return {
    q: params.get("q") ?? "",
    locale: params.get("_setlocale") ?? undefined,
    filters,
  };
}

/** Writes the question into the target lens's URL. */
export function lensHref(lens: SearchLens, siteName: string, query: LensQuery): string {
  const params = new URLSearchParams();
  if (query.q.trim()) params.set("q", query.q.trim());
  if (query.locale) params.set("_setlocale", query.locale);
  for (const [field, values] of Object.entries(query.filters)) {
    for (const value of values ?? []) {
      if (!field || !value) continue;
      if (usesFqDialect(lens)) params.append("fq", `${field}:${value}`);
      else params.append(field, value);
    }
  }
  const qs = params.toString();
  const suffix = qs ? "?" + qs : "";
  return `${LENS_PREFIX[lens]}/${siteName}${suffix}`;
}

/**
 * Param names the lens ignores — surfaced so a page can tell an operator which
 * part of a hand-edited link had no effect instead of silently dropping it.
 * Always empty for `/ann`, where every non-reserved param *is* a filter.
 */
export function unknownLensParams(lens: SearchLens, params: URLSearchParams): string[] {
  if (!usesFqDialect(lens)) return [];
  return [...new Set([...params.keys()].filter((key) => !FQ_RESERVED.has(key)))];
}
