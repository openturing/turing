import { BadgeColorful } from "@/components/badge-colorful";
import { sanitizeHighlight } from "@/lib/sanitize-html";
import { Button } from "@/components/ui/button";
import type { ResolvedDocument, TurDocument } from "@viglet/turing-react-sdk";
import { useTuringClickTracking } from "@viglet/turing-react-sdk";
import { IconBraces, IconCalendar, IconCopy, IconExternalLink, IconPhoto } from "@tabler/icons-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { NavLink } from "react-router-dom";

interface ResultCardProps {
  document: ResolvedDocument;
  raw: TurDocument;
  siteName: string;
  /** 1-based rank of the result within the page; powers click-position metrics. */
  position: number;
  defaultImageField?: string;
  onNavigate: (href: string) => void;
  onViewJson: (doc: Record<string, unknown>) => void;
}

function resolveDocumentId(doc: ResolvedDocument, raw: TurDocument): string {
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

export function ResultCard({ document: doc, raw, siteName, position, defaultImageField, onNavigate, onViewJson }: Readonly<ResultCardProps>) {
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
            {Array.isArray(raw.metadata) &&
              raw.metadata.map((meta) => {
                if (!meta?.text || !meta?.href) return null;
                return (
                  <BadgeColorful
                    key={`${meta.text}-${meta.href}`}
                    text={meta.text}
                    href={meta.href}
                    onClick={onNavigate}
                  />
                );
              })}
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
