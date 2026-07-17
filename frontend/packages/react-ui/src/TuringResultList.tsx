import type { ReactNode } from "react";

/**
 * Minimal structural shape of a raw Turing document — redeclared locally so this
 * package stays zero-runtime-dependency (it cannot import the core
 * `@viglet/turing-sdk` types without breaking viglet.com's `file:` install; see
 * the html/d2 splitter precedent). Structurally compatible with the SDK's
 * `TurDocument`, so the SDK can re-export this component with no consumer churn.
 */
export interface TuringRawDocument {
  elevate: boolean;
  fields: Record<string, unknown>;
  metadata: { href: string; text: string }[];
}

/** Minimal structural shape of a resolved (default-field-mapped) document. */
export interface TuringResolvedDocument {
  url: string;
  title: string;
  description: string;
  date: string;
  image: string;
  text: string;
  raw: TuringRawDocument;
}

export interface TuringResultListProps {
  documents: TuringResolvedDocument[];
  /** Render prop for a single result row. */
  itemComponent: (props: {
    document: TuringResolvedDocument;
    raw: TuringRawDocument;
    index: number;
  }) => ReactNode;
  /** Rendered when there are no documents (and not loading). */
  emptyComponent?: () => ReactNode;
  /** Rendered while {@link isLoading} is true. */
  loadingComponent?: () => ReactNode;
  isLoading?: boolean;
  className?: string;
  /**
   * T463 (Block Z) — analytics passthrough. Fires when a result row is clicked,
   * with the resolved document and its 0-based index. Wire it to the SDK's
   * `trackResultClick(documentId, position)` (1-based) so a single prop lights
   * up both server CTR and the GA4 `turing_search_result_click` event without
   * editing each `itemComponent`. Self-contained — no SDK import.
   */
  onResultClick?: (document: TuringResolvedDocument, index: number) => void;
}

/**
 * Headless search-result list — renders documents through a render-prop
 * (`itemComponent`) with optional empty/loading slots, and tags the list with
 * ARIA `list`/`listitem` roles.
 *
 * <p>Pure renderer: it takes data + callbacks via props and touches no hook,
 * so it carries no API/axios coupling or baked-in styling. Migrated from
 * {@code @viglet/turing-react-sdk} (T306); the SDK keeps re-exporting it so
 * existing imports are unchanged.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export function TuringResultList({
  documents,
  itemComponent,
  emptyComponent,
  loadingComponent,
  isLoading = false,
  className,
  onResultClick,
}: Readonly<TuringResultListProps>) {
  if (isLoading && loadingComponent) {
    return <div className={className}>{loadingComponent()}</div>;
  }

  if (!documents.length && emptyComponent) {
    return <div className={className}>{emptyComponent()}</div>;
  }

  return (
    <div className={className} role="list">
      {documents.map((doc, index) => {
        // A document's `url` is NOT a reliable React key: a real corpus can hold
        // two hits with the same url (a page and one of its sections, the same
        // page indexed under two categories, a PDF referenced twice). Duplicate
        // keys make React mis-reconcile on the next query — a stale row lingers
        // as a "ghost" duplicate. Prefer the document's stable id when present,
        // and otherwise fall back to `url#index`, which is always unique.
        const rawId = doc.raw?.fields?.id;
        const key = rawId != null ? String(rawId) : `${doc.url}#${index}`;
        return (
          <div
            key={key}
            role="listitem"
            onClick={onResultClick ? () => onResultClick(doc, index) : undefined}
          >
            {itemComponent({ document: doc, raw: doc.raw, index })}
          </div>
        );
      })}
    </div>
  );
}

TuringResultList.displayName = "TuringResultList";
