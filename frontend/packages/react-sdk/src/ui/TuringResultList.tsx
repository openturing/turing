import type { ReactNode } from "react";
import type { ResolvedDocument, TurDocument } from "../core/types";

interface TuringResultListProps {
  documents: ResolvedDocument[];
  itemComponent: (props: { document: ResolvedDocument; raw: TurDocument; index: number }) => ReactNode;
  emptyComponent?: () => ReactNode;
  loadingComponent?: () => ReactNode;
  isLoading?: boolean;
  className?: string;
}

/**
 * Renders search results via a slot/render-prop pattern.
 *
 * @since 2026.2.0
 */
export function TuringResultList({
  documents,
  itemComponent,
  emptyComponent,
  loadingComponent,
  isLoading = false,
  className,
}: TuringResultListProps) {
  if (isLoading && loadingComponent) {
    return <div className={className}>{loadingComponent()}</div>;
  }

  if (!documents.length && emptyComponent) {
    return <div className={className}>{emptyComponent()}</div>;
  }

  return (
    <div className={className} role="list">
      {documents.map((doc, index) => (
        <div key={doc.url || index} role="listitem">
          {itemComponent({ document: doc, raw: doc.raw, index })}
        </div>
      ))}
    </div>
  );
}
