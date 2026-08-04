import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  TuringMarkdown,
  useTuringAnswer,
  type AnswerFilters,
} from "@viglet/turing-react-sdk";
import {
  IconExternalLink,
  IconLoader2,
  IconSearch,
  IconSparkles,
  IconX,
} from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams, useSearchParams } from "react-router-dom";

/**
 * T852 / §LXIV.1 (Block BQ) — `/answer/{siteName}`, the **third search lens**, a
 * peer route beside the keyword `/sn` page and the vector `/ann` page. Where
 * `/sn` shows what matched and `/ann` shows what the vector store retrieved,
 * this one shows the synthesized answer produced by the shipped streaming
 * `GET /api/sn/{site}/answer` endpoint (T833), read through the headless
 * `useTuringAnswer` hook of `@viglet/turing-react-sdk`.
 *
 * <p>Every input that defines an answer lives in the URL — `q`, `_setlocale`
 * and the repeated `fq=field:value` steering filter set T838 threads — so the
 * question an operator wants to discuss is a link. That is distinct from the
 * T844 permalink, which snapshots a *produced* answer; this only makes the
 * *input* reproducible, so re-opening the link re-asks and re-streams.
 *
 * <p>Adds no backend. Fail-open by inheritance: a mid-stream `error` event lands
 * in the hook's `error` and renders as a banner, never as a thrown stream.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

/** URL params that are not steering filters. */
const RESERVED_PARAMS = new Set(["q", "_setlocale", "fq"]);

/** Reads the repeated `fq=field:value` params into the SDK's filter map. */
function parseFilters(params: URLSearchParams): AnswerFilters {
  const filters: AnswerFilters = {};
  for (const raw of params.getAll("fq")) {
    const separator = raw.indexOf(":");
    if (separator <= 0) continue;
    const field = raw.slice(0, separator);
    const value = raw.slice(separator + 1);
    if (!field || !value) continue;
    filters[field] ??= [];
    filters[field].push(value);
  }
  return filters;
}

export default function AnswerSearchPage() {
  const { siteName } = useParams<{ siteName: string }>();
  const site = siteName || "";
  const { t } = useTranslation();

  const [searchParams, setSearchParams] = useSearchParams();
  const urlQuery = searchParams.get("q") ?? "";
  const locale = searchParams.get("_setlocale") ?? "";

  const [inputValue, setInputValue] = useState(urlQuery);

  // The filter map is derived from the URL, so it is a new object every render;
  // key the ask effect off its serialization, not its identity.
  const filters = useMemo(() => parseFilters(searchParams), [searchParams]);
  const filtersKey = useMemo(() => JSON.stringify(filters), [filters]);

  const { answer, sources, citations, followups, status, error, isStreaming, ask, signal } =
    useTuringAnswer({ siteName: site, locale: locale || undefined });

  // The URL is the single source of truth: any change to q / locale / filters
  // re-asks. `ask` itself ignores a blank or `*` query, so an unqueried landing
  // renders the empty state instead of firing a request.
  useEffect(() => {
    void ask(urlQuery, { filters: Object.keys(filters).length > 0 ? filters : undefined });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [urlQuery, locale, filtersKey]);

  // A link pasted into the address bar must repopulate the visible input.
  useEffect(() => setInputValue(urlQuery), [urlQuery]);

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const next = new URLSearchParams(searchParams);
    const trimmed = inputValue.trim();
    if (trimmed) next.set("q", trimmed);
    else next.delete("q");
    setSearchParams(next);
  };

  const removeFilter = (field: string, value: string) => {
    // T845 — narrowing away after reading is the clean relevance-miss signal.
    if (status === "success") signal("FACET_REFINED_AFTER_ANSWER");
    const next = new URLSearchParams(searchParams);
    const kept = next.getAll("fq").filter((raw) => raw !== `${field}:${value}`);
    next.delete("fq");
    kept.forEach((raw) => next.append("fq", raw));
    setSearchParams(next);
  };

  const askFollowup = (question: string) => {
    signal("FOLLOW_UP_TAKEN");
    const next = new URLSearchParams(searchParams);
    next.set("q", question);
    setSearchParams(next);
  };

  const constraints = Object.entries(filters).flatMap(([field, values]) =>
    (values ?? []).map((value) => [field, value] as const),
  );
  const unknownParams = [...searchParams.keys()].filter((key) => !RESERVED_PARAMS.has(key));

  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col">
      <header className="border-b border-border/60 bg-background/95 backdrop-blur sticky top-0 z-10">
        <div className="container mx-auto px-4 lg:px-8 py-4">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center">
              <IconSparkles className="size-5 text-white" />
            </div>
            <div>
              <h1 className="text-lg font-semibold">{t("answer.title")}</h1>
              <p className="text-xs text-muted-foreground">{site}</p>
            </div>
          </div>
          <form onSubmit={submit} className="flex gap-2">
            <div className="relative flex-1">
              <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
              <Input
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                placeholder={t("answer.placeholder")}
                className="pl-9"
              />
            </div>
            <Button type="submit" disabled={isStreaming || !inputValue.trim()}>
              {isStreaming ? <IconLoader2 className="size-4 animate-spin" /> : t("answer.ask")}
            </Button>
          </form>
          {constraints.length > 0 && (
            <div className="flex flex-wrap items-center gap-1.5 mt-3">
              <span className="text-xs text-muted-foreground">{t("answer.within")}</span>
              {constraints.map(([field, value]) => (
                <button
                  key={`${field}:${value}`}
                  type="button"
                  onClick={() => removeFilter(field, value)}
                  aria-label={t("answer.removeConstraint", { field, value })}
                  className="inline-flex items-center gap-1 rounded-full border border-blue-500/40 bg-blue-500/10 px-2 py-0.5 text-[11px] text-blue-700 dark:text-blue-300 hover:bg-blue-500/20 transition-colors"
                >
                  <span className="text-muted-foreground">{field}:</span>
                  <span className="truncate max-w-50">{value}</span>
                  <IconX className="size-3" />
                </button>
              ))}
            </div>
          )}
        </div>
      </header>

      <main className="flex-1 container mx-auto px-4 lg:px-8 py-6 max-w-4xl">
        {unknownParams.length > 0 && (
          <p className="mb-4 text-xs text-muted-foreground">
            {t("answer.ignoredParams", { params: unknownParams.join(", ") })}
          </p>
        )}

        {error && (
          <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive mb-4" role="alert">
            {error}
          </div>
        )}

        {!urlQuery.trim() && !answer && !error && (
          <div className="text-center py-16 text-muted-foreground">
            <IconSparkles className="mx-auto size-8 mb-3 text-blue-500/60" />
            <p>{t("answer.empty")}</p>
          </div>
        )}

        {isStreaming && !answer && (
          <div className="flex items-center gap-2 text-sm text-muted-foreground py-6">
            <IconLoader2 className="size-4 animate-spin" />
            {t("answer.thinking")}
          </div>
        )}

        {answer && (
          <article className="prose prose-sm dark:prose-invert max-w-none rounded-xl border border-border/60 bg-card p-5">
            <TuringMarkdown>{answer}</TuringMarkdown>
          </article>
        )}

        {sources.length > 0 && (
          <section className="mt-6">
            <h2 className="text-sm font-semibold mb-3">{t("answer.sources")}</h2>
            <ol className="space-y-2">
              {sources.map((source) => (
                <li
                  key={source.rank ?? source.sourceId}
                  id={source.rank != null ? `turing-source-${source.rank}` : undefined}
                  className="flex items-start gap-2 rounded-lg border border-border/60 bg-card px-3 py-2 text-sm"
                >
                  {source.rank != null && (
                    <Badge variant="secondary" className="shrink-0 text-[10px] tabular-nums">
                      [{source.rank}]
                    </Badge>
                  )}
                  <span className="min-w-0 flex-1 truncate" title={source.title}>
                    {source.url ? (
                      <a
                        href={source.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        onClick={() => signal("CITATION_CLICKED")}
                        className="inline-flex items-center gap-1 text-blue-600 hover:underline dark:text-blue-400"
                      >
                        {source.title}
                        <IconExternalLink className="size-3.5 shrink-0" />
                      </a>
                    ) : (
                      source.title
                    )}
                  </span>
                </li>
              ))}
            </ol>
          </section>
        )}

        {followups.length > 0 && (
          <section className="mt-6">
            <h2 className="text-sm font-semibold mb-3">{t("answer.followups")}</h2>
            <div className="flex flex-wrap gap-2">
              {followups.map((question) => (
                <button
                  key={question}
                  type="button"
                  onClick={() => askFollowup(question)}
                  className="rounded-full border border-border/60 bg-transparent px-3 py-1.5 text-xs transition-colors hover:border-blue-500/40 hover:bg-blue-500/5"
                >
                  {question}
                </button>
              ))}
            </div>
          </section>
        )}

        {status === "success" && citations.length === 0 && answer && (
          <p className="mt-6 text-xs text-muted-foreground">{t("answer.noCitations")}</p>
        )}
      </main>
    </div>
  );
}
