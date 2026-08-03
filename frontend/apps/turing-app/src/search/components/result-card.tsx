import { BadgeColorful } from "@/components/badge-colorful";
import { sanitizeHighlight } from "@/lib/sanitize-html";
import { Button } from "@/components/ui/button";
import type {
  ResolvedDocument,
  TurDocument,
  TurDocumentMetadata,
  TurFacetToggleTarget,
  TurStructuredDocument,
  TurStructuredDocumentMetadata,
} from "@viglet/turing-react-sdk";
import { useTuringClickTracking } from "@viglet/turing-react-sdk";
import { IconBraces, IconCalendar, IconCopy, IconExternalLink, IconPhoto } from "@tabler/icons-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { NavLink } from "react-router-dom";

/**
 * T864 — either document shape. POST-native (the default) the facet chips carry
 * a `filterQuery` instead of an `href`.
 */
type AnyRawDocument = TurDocument | TurStructuredDocument;

interface ResultCardProps {
  document: ResolvedDocument;
  raw: AnyRawDocument;
  siteName: string;
  /** 1-based rank of the result within the page; powers click-position metrics. */
  position: number;
  defaultImageField?: string;
  /**
   * Legacy (`postNative: false`) only: follow a chip's server-rendered href.
   *
   * @deprecated T867 — POST-native the chip has no href. Kept so the legacy
   *   transport keeps working.
   */
  onNavigate: (href: string) => void;
  /**
   * T867 / §LXVII.3 — apply a structured chip's filter to the current search. Same
   * call the facet rail makes for the same `field:value`, so a result's tag filters
   * the search exactly as it did before T710.
   */
  onToggleFacet?: (item: TurFacetToggleTarget) => void;
  onViewJson: (doc: Record<string, unknown>) => void;
}

function resolveDocumentId(doc: ResolvedDocument, raw: AnyRawDocument): string {
  const fieldId = raw.fields?.id;
  return doc.url || (fieldId != null ? String(fieldId) : "") || doc.title || "";
}

/** A short, human label for a duplicate source: its `source_apps`, else the URL host, else the title. */
function duplicateSourceLabel(member: { source?: string | null; url?: string | null; title?: string | null }): string {
  if (member.source) return member.source;
  if (member.url) {
    try {
      return new URL(member.url, "https://placeholder.local").hostname.replace(/^www\./, "") || member.url;
    } catch {
      return member.url;
    }
  }
  return member.title || "—";
}

/**
 * T867 / §LXVII.3 — one of a result's facet chips.
 *
 * <p>T864 gave the structured chip a `filterQuery` and made it render, but left it
 * a dead badge: only the legacy `href` shape was clickable. Both are actions now,
 * and the chip is wrapped in a real `<button>` when it has one — `BadgeColorful`
 * only fires its `onClick` for an `href`, and passing a filter query as an href is
 * exactly the "looks like a link, isn't one" confusion T860 removed.
 */
function DocumentFacetChip({
  meta,
  onNavigate,
  onToggleFacet,
}: Readonly<{
  meta: TurDocumentMetadata | TurStructuredDocumentMetadata;
  onNavigate: (href: string) => void;
  onToggleFacet?: (item: TurFacetToggleTarget) => void;
}>) {
  const { t } = useTranslation();

  if (!meta.text) return null;

  if ("href" in meta) {
    return <BadgeColorful text={meta.text} href={meta.href} onClick={onNavigate} />;
  }
  if (!meta.filterQuery) return null;
  if (!onToggleFacet) return <BadgeColorful text={meta.text} />;

  return (
    <button
      type="button"
      onClick={() => onToggleFacet(meta)}
      aria-pressed={meta.selected}
      title={meta.selected ? t("search.clearFacet") : t("search.filterBy", { value: meta.text })}
      className="cursor-pointer rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40"
    >
      <BadgeColorful
        text={meta.text}
        className={meta.selected ? "ring-1 ring-blue-500/50" : undefined}
      />
    </button>
  );
}

export function ResultCard({ document: doc, raw, siteName, position, defaultImageField, onNavigate, onToggleFacet, onViewJson }: Readonly<ResultCardProps>) {
  const { t, i18n } = useTranslation();
  const { trackClick } = useTuringClickTracking();
  const [imgError, setImgError] = useState(false);

  if (!doc.url) return null;

  const handleResultClick = () => {
    trackClick(resolveDocumentId(doc, raw), position);
  };

  const isInternal = doc.url.startsWith("/") && !doc.url.startsWith("//");
  const rawImage = doc.image || (defaultImageField && raw.fields?.[defaultImageField] as string) || "";
  const imageSrc = rawImage && !rawImage.startsWith("http")
    ? `/sn/${siteName}/${rawImage}`
    : rawImage;
  const hasImage = !!imageSrc && !imgError;

  // T390 — duplicate cluster: the same entity from other sources. Show the
  // siblings (excluding this result) so the catalog collapses the duplicates.
  const currentId = raw.fields?.id != null ? String(raw.fields.id) : undefined;
  const duplicateMembers = raw.duplicateCluster?.duplicate
    ? raw.duplicateCluster.members.filter((m) => m.id !== currentId)
    : [];

  return (
    <article className="group relative rounded-xl border border-border/60 bg-card p-5 transition-all duration-200 hover:border-blue-500/30 hover:shadow-lg hover:shadow-blue-500/5">
      {/* Subtle left accent */}
      <div className="absolute left-0 top-4 bottom-4 w-0.5 rounded-full bg-gradient-to-b from-blue-500 to-indigo-500 opacity-0 group-hover:opacity-100 transition-opacity" />

      <div className="flex gap-4">
        {/* Thumbnail */}
        {imageSrc && (
          <div className="shrink-0">
            {hasImage ? (
              <div className="w-28 h-20 rounded-lg overflow-hidden ring-1 ring-border/50 group-hover:ring-blue-500/30 transition-all">
                <img
                  src={imageSrc}
                  alt=""
                  className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                  loading="lazy"
                  onError={() => setImgError(true)}
                />
              </div>
            ) : (
              <div className="w-28 h-20 rounded-lg bg-muted/50 ring-1 ring-border/50 flex items-center justify-center">
                <IconPhoto className="size-6 text-muted-foreground/30" />
              </div>
            )}
          </div>
        )}

        <div className="flex-1 min-w-0">
          {/* Title */}
          <h4 className="text-base font-semibold leading-snug mb-1.5">
            {isInternal ? (
              <NavLink
                to={doc.url}
                onClick={handleResultClick}
                className="text-foreground hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
                dangerouslySetInnerHTML={{ __html: sanitizeHighlight(doc.title || doc.url) }}
              />
            ) : (
              <a
                href={doc.url}
                onClick={handleResultClick}
                className="text-foreground hover:text-blue-600 dark:hover:text-blue-400 transition-colors inline-flex items-center gap-1"
                target="_blank"
                rel="noopener noreferrer"
              >
                <span dangerouslySetInnerHTML={{ __html: sanitizeHighlight(doc.title || doc.url) }} />
                <IconExternalLink className="size-3.5 shrink-0 opacity-40" />
              </a>
            )}
          </h4>

          {/* URL breadcrumb */}
          <p className="text-xs text-muted-foreground/60 mb-2 truncate">{doc.url}</p>

          {/* Description */}
          {doc.description && (
            <p
              className="text-sm text-muted-foreground leading-relaxed mb-3 line-clamp-2"
              dangerouslySetInnerHTML={{ __html: sanitizeHighlight(doc.description) }}
            />
          )}

          {/* Metadata row: badges + date */}
          <div className="flex flex-wrap items-center gap-2">
            {/* T864 — the chip's identity is its text plus whatever the response
                gave us to act on: an `href` in legacy mode, a `filterQuery`
                POST-native. T867 — both are now actions. */}
            {Array.isArray(raw.metadata) &&
              raw.metadata.map((meta) => (
                <DocumentFacetChip
                  key={`${meta.text}-${"href" in meta ? meta.href : meta.filterQuery}`}
                  meta={meta}
                  onNavigate={onNavigate}
                  onToggleFacet={onToggleFacet}
                />
              ))}
            {doc.date && (
              <span className="inline-flex items-center gap-1 text-xs text-muted-foreground/70">
                <IconCalendar className="size-3" />
                {t("search.updated", { date: new Date(doc.date).toLocaleDateString(i18n.language, { year: "numeric", month: "long", day: "numeric" }) })}
              </span>
            )}
          </div>

          {/* Duplicate sources (T390) */}
          {duplicateMembers.length > 0 && (
            <div className="mt-3 rounded-lg border border-amber-500/20 bg-amber-500/5 px-3 py-2">
              <p className="mb-1.5 inline-flex items-center gap-1.5 text-xs font-medium text-amber-700 dark:text-amber-400">
                <IconCopy className="size-3.5" />
                {t("search.alsoAvailableOn", { count: duplicateMembers.length })}
              </p>
              <div className="flex flex-wrap gap-1.5">
                {duplicateMembers.map((member) => {
                  const label = duplicateSourceLabel(member);
                  const chipClass =
                    "inline-flex items-center gap-1 rounded-md bg-background/60 px-2 py-0.5 text-xs text-muted-foreground ring-1 ring-border/50 transition-colors hover:text-amber-700 hover:ring-amber-500/40 dark:hover:text-amber-400";
                  return member.url ? (
                    <a
                      key={member.id}
                      href={member.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      title={t("search.duplicateSource")}
                      className={chipClass}
                    >
                      {label}
                      <IconExternalLink className="size-3 shrink-0 opacity-40" />
                    </a>
                  ) : (
                    <span key={member.id} className={chipClass} title={t("search.duplicateSource")}>
                      {label}
                    </span>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* JSON button */}
        <div className="shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
          <Button
            variant="ghost"
            size="icon-sm"
            title={t("search.viewJson")}
            className="text-muted-foreground hover:text-foreground"
            onClick={() => onViewJson(raw as unknown as Record<string, unknown>)}
          >
            <IconBraces className="size-4" />
          </Button>
        </div>
      </div>
    </article>
  );
}
