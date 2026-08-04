import { Badge } from "@/components/ui/badge";
import { fetchAnswerShare, type TurAnswerShareView } from "@viglet/turing-react-sdk";
import { TuringMarkdown } from "@viglet/turing-react-ui";
import { IconExternalLink, IconLoader2, IconSparkles } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router-dom";
import { lensHref } from "../lib/search-lens";

/**
 * T890 / §LXIV.6 (Block BQ) — the read-only view of a shared answer permalink
 * (T844). The `/answer` page's Share button snapshots a *produced* answer and
 * copies a link here, so a wrong answer leaves the console as a resolvable
 * artefact rather than a screenshot: the exact prose, the exact sources and the
 * filters it was synthesized within, as they were when it was minted.
 *
 * <p>Deliberately NOT a replay: re-asking the question would produce a different
 * answer and destroy the evidence. The link back into the live lens carries the
 * original question so a replay is one explicit click.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

/** The snapshot fields ride the wire as opaque JSON strings; parse defensively. */
function parseJson<T>(raw: string | null | undefined, fallback: T): T {
  if (!raw) return fallback;
  try {
    return (JSON.parse(raw) as T) ?? fallback;
  } catch {
    // A snapshot written by an older client must not blank the whole page.
    return fallback;
  }
}

interface SnapshotSource {
  sourceId?: string;
  title?: string;
  url?: string | null;
  rank?: number | null;
  score?: number | null;
}

export default function AnswerSharePage() {
  const { siteName, shareId } = useParams<{ siteName: string; shareId: string }>();
  const site = siteName || "";
  const { t } = useTranslation();

  const [view, setView] = useState<TurAnswerShareView | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let current = true;
    setLoading(true);
    // fetchAnswerShare swallows its own errors and resolves null, so a missing or
    // expired id lands in the not-found branch rather than an unhandled rejection.
    void fetchAnswerShare(site, shareId ?? "").then((data) => {
      if (!current) return;
      setView(data);
      setLoading(false);
    });
    return () => {
      current = false;
    };
  }, [site, shareId]);

  const sources = parseJson<SnapshotSource[]>(view?.sourcesJson, []);
  const filters = parseJson<Record<string, string[]>>(view?.filtersJson, {});
  const constraints = Object.entries(filters).flatMap(([field, values]) =>
    (values ?? []).map((value) => [field, value] as const),
  );

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background text-muted-foreground">
        <IconLoader2 className="size-5 animate-spin" />
      </div>
    );
  }

  if (!view) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-2 bg-background text-foreground">
        <p className="text-sm text-muted-foreground">{t("answer.shareNotFound")}</p>
        <Link to={`/answer/${site}`} className="text-sm text-blue-600 hover:underline dark:text-blue-400">
          {t("answer.openAnswerSearch")}
        </Link>
      </div>
    );
  }

  const replayHref = lensHref("answer", site, {
    q: view.query,
    locale: undefined,
    filters,
  });

  return (
    <div className="flex min-h-screen flex-col bg-background text-foreground">
      <header className="sticky top-0 z-10 border-b border-border/60 bg-background/95 backdrop-blur">
        <div className="container mx-auto flex items-center gap-3 px-4 py-4 lg:px-8">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600">
            <IconSparkles className="size-5 text-white" />
          </div>
          <div className="min-w-0 flex-1">
            <h1 className="truncate text-lg font-semibold">{view.query}</h1>
            <p className="text-xs text-muted-foreground">
              {view.siteName || site}
              {view.answeredAt ? ` · ${new Date(view.answeredAt).toLocaleString()}` : ""}
            </p>
          </div>
          <Badge variant="secondary" className="shrink-0 text-[10px]">
            {t("answer.shareSnapshot")}
          </Badge>
        </div>
      </header>

      <main className="container mx-auto max-w-4xl flex-1 px-4 py-6 lg:px-8">
        {constraints.length > 0 && (
          <div className="mb-4 flex flex-wrap items-center gap-1.5">
            <span className="text-xs text-muted-foreground">{t("answer.within")}</span>
            {constraints.map(([field, value]) => (
              <span
                key={`${field}:${value}`}
                className="inline-flex items-center gap-1 rounded-full border border-border/60 px-2 py-0.5 text-[11px]"
              >
                <span className="text-muted-foreground">{field}:</span>
                {value}
              </span>
            ))}
          </div>
        )}

        <article className="prose prose-sm max-w-none rounded-xl border border-border/60 bg-card p-5 dark:prose-invert">
          <TuringMarkdown>{view.answerText ?? ""}</TuringMarkdown>
        </article>

        {sources.length > 0 && (
          <section className="mt-6">
            <h2 className="mb-3 text-sm font-semibold">{t("answer.sources")}</h2>
            <ol className="space-y-2">
              {sources.map((source, index) => (
                <li
                  key={source.sourceId ?? `${source.title}-${index}`}
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
                        className="inline-flex items-center gap-1 text-blue-600 hover:underline dark:text-blue-400"
                      >
                        {source.title}
                        <IconExternalLink className="size-3.5 shrink-0" />
                      </a>
                    ) : (
                      source.title
                    )}
                  </span>
                  {typeof source.score === "number" && (
                    <Badge variant="secondary" className="shrink-0 text-[10px] tabular-nums">
                      {t("answer.score")}: {source.score.toFixed(3)}
                    </Badge>
                  )}
                </li>
              ))}
            </ol>
          </section>
        )}

        <p className="mt-6 text-xs text-muted-foreground">
          {t("answer.shareReplayHint")}{" "}
          <Link to={replayHref} className="text-blue-600 hover:underline dark:text-blue-400">
            {t("answer.shareReplay")}
          </Link>
        </p>
      </main>
    </div>
  );
}
