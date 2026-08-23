import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useTuringAnswer } from "@viglet/turing-react-sdk";
import {
  TuringAnswerProvenance,
  TuringMarkdown,
  citationMarkdownComponents,
  rehypeCitationAnchors,
} from "@viglet/turing-react-ui";
import {
  IconAlertTriangle,
  IconBraces,
  IconExternalLink,
  IconLoader2,
  IconRefresh,
  IconSearch,
  IconShare2,
  IconSparkles,
  IconThumbDown,
  IconThumbUp,
  IconX,
} from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { useParams, useSearchParams } from "react-router-dom";
import { JsonSheet } from "../components/json-sheet";
import { SearchLensSwitcher } from "../components/search-lens-switcher";
import { readLensQuery, unknownLensParams } from "../lib/search-lens";

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
 * <p>T854 / §LXIV.3 — this is the **operator** view of an answer, so the reader
 * defaults are turned inside out here: the provenance panel starts expanded, the
 * research trace is shown, each source card carries its footnote rank and
 * retrieval score, and the raw document is one click away in the same
 * `JsonSheet` `/ann` uses. Nothing extra is computed — every field already rides
 * the `citations` / `trace` / `trace_step` events.
 *
 * <p>Adds no backend. Fail-open by inheritance: a mid-stream `error` event lands
 * in the hook's `error` and renders as a banner, never as a thrown stream.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

export default function AnswerSearchPage() {
  const { siteName } = useParams<{ siteName: string }>();
  const site = siteName || "";
  const { t } = useTranslation();

  const [searchParams, setSearchParams] = useSearchParams();
  const urlQuery = searchParams.get("q") ?? "";
  const locale = searchParams.get("_setlocale") ?? "";

  const [inputValue, setInputValue] = useState(urlQuery);
  const [jsonView, setJsonView] = useState<Record<string, unknown> | null>(null);
  // T890 — the thumbs are one-shot per answer, so the row acknowledges the
  // judgement instead of inviting a second one that the miner would double-count.
  const [verdict, setVerdict] = useState<"up" | "down" | null>(null);
  const [sharing, setSharing] = useState(false);
  const [checkingFreshness, setCheckingFreshness] = useState(false);

  // T855 — the lens-independent question, read through the shared translator so
  // the switcher can hand the same `q` / locale / filters to `/sn` and `/ann`.
  const lensQuery = useMemo(() => readLensQuery("answer", searchParams), [searchParams]);
  // Derived from the URL, so it is a new object every render; key the ask effect
  // off its serialization, not its identity.
  const filters = lensQuery.filters;
  const filtersKey = useMemo(() => JSON.stringify(filters), [filters]);

  const {
    answer,
    sources,
    citations,
    followups,
    trace,
    traceSteps,
    status,
    error,
    isStreaming,
    staleSources,
    ask,
    checkFreshness,
    share,
    signal,
  } = useTuringAnswer({ siteName: site, locale: locale || undefined });

  // T854 — the citation projection keyed by footnote, so a source card can show
  // the rank breakdown and the raw JSON the answer engine actually cited.
  const citationByRank = useMemo(
    () => new Map(citations.map((citation) => [citation.rank, citation])),
    [citations],
  );
  // Only completed tool calls belong in the trace: a `start` with no `end` is an
  // in-flight step, and rendering it would double every entry once it lands.
  const completedSteps = traceSteps.filter((step) => step.phase === "end");

  // The URL is the single source of truth: any change to q / locale / filters
  // re-asks. `ask` itself ignores a blank or `*` query, so an unqueried landing
  // renders the empty state instead of firing a request.
  useEffect(() => {
    void ask(urlQuery, { filters: Object.keys(filters).length > 0 ? filters : undefined });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [urlQuery, locale, filtersKey]);

  // T1017 — adjusted during render rather than in an effect. Both of these
  // reset when the question changes: the visible input must repopulate for a
  // link pasted into the address bar, and T890's verdict must clear so the
  // thumbs are live again rather than showing the last answer's
  // acknowledgement. Done in an effect, each cost a committed render showing the
  // previous question's input and its stale verdict before being corrected.
  // React re-runs this component before painting, so nothing intermediate ships.
  // Joined with a character `filtersKey` cannot contain: it is JSON, so a raw
  // newline never appears inside it and the two halves stay unambiguous.
  const askedKey = `${urlQuery}\n${filtersKey}`;
  const [lastAskedKey, setLastAskedKey] = useState(askedKey);
  if (askedKey !== lastAskedKey) {
    setLastAskedKey(askedKey);
    setInputValue(urlQuery);
    setVerdict(null);
  }

  const onShare = async () => {
    setSharing(true);
    try {
      const shareId = await share();
      const url = `${window.location.origin}/answer/${site}/share/${shareId}`;
      await navigator.clipboard.writeText(url);
      toast.success(t("answer.shareCopied"));
    } catch {
      // Fail-closed on the toast only: nothing about the answer changes.
      toast.error(t("answer.shareFailed"));
    } finally {
      setSharing(false);
    }
  };

  const onCheckFreshness = async () => {
    setCheckingFreshness(true);
    try {
      await checkFreshness();
    } finally {
      setCheckingFreshness(false);
    }
  };

  const onVerdict = (next: "up" | "down") => {
    if (verdict) return;
    signal(next === "up" ? "THUMBS_UP" : "THUMBS_DOWN");
    setVerdict(next);
  };

  const reAnswer = () => {
    signal("RE_ANSWER_REQUESTED");
    void ask(urlQuery, { filters: Object.keys(filters).length > 0 ? filters : undefined });
  };

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
  const unknownParams = unknownLensParams("answer", searchParams);

  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col">
      <header className="border-b border-border/60 bg-background/95 backdrop-blur sticky top-0 z-10">
        <div className="container mx-auto px-4 lg:px-8 py-4">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center">
              <IconSparkles className="size-5 text-white" />
            </div>
            <div className="min-w-0 flex-1">
              <h1 className="text-lg font-semibold">{t("answer.title")}</h1>
              <p className="text-xs text-muted-foreground">{site}</p>
            </div>
            {/* T855 — replay the same question as keyword / vector / answer. */}
            <SearchLensSwitcher current="answer" siteName={site} query={lensQuery} />
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
            {/* T836 — `[n]` markers become anchors onto the source cards below. */}
            <TuringMarkdown
              rehypePlugins={[rehypeCitationAnchors]}
              components={citationMarkdownComponents({
                citations: citations.map((citation) => ({
                  index: citation.rank,
                  label: citation.title ?? undefined,
                  citedText: citation.citedText ?? undefined,
                  url: citation.url ?? undefined,
                })),
              })}
            >
              {answer}
            </TuringMarkdown>
          </article>
        )}

        {/* T890 — the three things an operator does with an answer it has read:
            hand it over, re-check whether its sources moved, and judge it. */}
        {status === "success" && answer && (
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <Button variant="outline" size="sm" disabled={sharing} onClick={() => void onShare()}>
              {sharing ? (
                <IconLoader2 className="size-4 animate-spin" />
              ) : (
                <IconShare2 className="size-4" />
              )}
              {t("answer.share")}
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={checkingFreshness || citations.length === 0}
              onClick={() => void onCheckFreshness()}
              title={citations.length === 0 ? t("answer.freshnessNeedsCitations") : undefined}
            >
              {checkingFreshness ? (
                <IconLoader2 className="size-4 animate-spin" />
              ) : (
                <IconRefresh className="size-4" />
              )}
              {t("answer.checkFreshness")}
            </Button>
            <span className="ml-auto flex items-center gap-1.5 text-xs text-muted-foreground">
              {verdict ? t("answer.feedbackRecorded") : t("answer.feedback")}
              <Button
                variant="ghost"
                size="icon-sm"
                disabled={verdict !== null}
                aria-label={t("answer.thumbUp")}
                aria-pressed={verdict === "up"}
                onClick={() => onVerdict("up")}
                className={verdict === "up" ? "text-emerald-600 dark:text-emerald-400" : undefined}
              >
                <IconThumbUp className="size-4" />
              </Button>
              <Button
                variant="ghost"
                size="icon-sm"
                disabled={verdict !== null}
                aria-label={t("answer.thumbDown")}
                aria-pressed={verdict === "down"}
                onClick={() => onVerdict("down")}
                className={verdict === "down" ? "text-red-600 dark:text-red-400" : undefined}
              >
                <IconThumbDown className="size-4" />
              </Button>
            </span>
          </div>
        )}

        {staleSources.length > 0 && (
          <output className="mt-3 flex flex-wrap items-center gap-2 rounded-lg border border-amber-500/40 bg-amber-500/5 px-3 py-2 text-xs">
            <IconAlertTriangle className="size-4 text-amber-600 dark:text-amber-400" />
            {t("answer.stale", { count: staleSources.length })}
            <Button variant="outline" size="sm" onClick={reAnswer}>
              {t("answer.reanswer")}
            </Button>
          </output>
        )}

        {/* T854 — expanded by default: on the operator page "why did it say that?"
            is the question, so the audit trail is not behind a click. */}
        {answer && (
          <TuringAnswerProvenance
            trace={trace}
            citations={citations.map((citation) => ({
              rank: citation.rank,
              title: citation.title,
              sourceId: citation.sourceId,
              rankingExplanation: citation.rankingExplanation,
            }))}
            defaultOpen
            labels={{
              title: t("answer.provenance"),
              kbMode: t("answer.kbMode"),
              rerank: t("answer.rerank"),
              rerankOff: t("answer.rerankOff"),
              sourceCount: t("answer.sourceCount"),
            }}
            classNames={{
              root: "mt-6 rounded-xl border border-border/60 bg-card px-4 py-3",
              summary: "cursor-pointer text-sm font-semibold",
              body: "mt-3 space-y-2 text-xs",
              row: "flex items-center gap-2",
              label: "text-muted-foreground",
              value: "font-mono",
              sources: "mt-3 space-y-2 border-t border-border/40 pt-3",
              source: "space-y-1",
              sourceTitle: "font-medium",
              explanation: "grid grid-cols-2 gap-x-3 gap-y-0.5 pl-3",
              explanationItem: "flex items-center gap-1.5",
            }}
          />
        )}

        {completedSteps.length > 0 && (
          <section className="mt-6 rounded-xl border border-border/60 bg-card px-4 py-3">
            <h2 className="text-sm font-semibold">{t("answer.trace")}</h2>
            <ol className="mt-2 space-y-1 text-xs">
              {completedSteps.map((step) => (
                <li key={`${step.callId}-end`} className="flex items-center gap-2">
                  <span className="font-mono">{step.name}</span>
                  {step.durationMs != null && (
                    <span className="text-muted-foreground tabular-nums">{step.durationMs}ms</span>
                  )}
                  {step.status && <span className="text-muted-foreground">{step.status}</span>}
                </li>
              ))}
            </ol>
          </section>
        )}

        {sources.length > 0 && (
          <section className="mt-6">
            <h2 className="text-sm font-semibold mb-3">{t("answer.sources")}</h2>
            <ol className="space-y-2">
              {sources.map((source) => {
                const citation = source.rank != null ? citationByRank.get(source.rank) : undefined;
                // The score rides the source event; fall back to the citation
                // projection, which carries it when the stream sent both.
                const score = source.score ?? citation?.score ?? null;
                return (
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
                    <span className="flex shrink-0 items-center gap-1.5">
                      {/* T890 — which source drifted is the actionable half of the
                          freshness verdict; the banner only says that one did. */}
                      {staleSources.includes(source.sourceId) && (
                        <Badge
                          variant="outline"
                          className="border-amber-500/40 text-[10px] text-amber-700 dark:text-amber-300"
                        >
                          {t("answer.sourceStale")}
                        </Badge>
                      )}
                      {typeof score === "number" && (
                        <Badge variant="secondary" className="text-[10px] tabular-nums">
                          {t("answer.score")}: {score.toFixed(3)}
                        </Badge>
                      )}
                      {source.keywordOnly && (
                        <Badge variant="outline" className="text-[10px]">
                          {t("answer.keywordOnly")}
                        </Badge>
                      )}
                      {source.chunkIndex != null && (
                        <Badge variant="outline" className="text-[10px] tabular-nums">
                          {t("answer.chunk")} {source.chunkIndex}
                        </Badge>
                      )}
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        title={t("answer.viewJson")}
                        aria-label={t("answer.viewJson")}
                        onClick={() =>
                          setJsonView({
                            source: source as unknown as Record<string, unknown>,
                            citation: (citation ?? null) as unknown as Record<string, unknown>,
                          })
                        }
                      >
                        <IconBraces className="size-4" />
                      </Button>
                    </span>
                  </li>
                );
              })}
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

      <JsonSheet data={jsonView} onClose={() => setJsonView(null)} />
    </div>
  );
}
