import type { ReactNode } from "react";
import type { TurPaginationItem } from "../core/types";

interface TuringPaginationProps {
  items: TurPaginationItem[];
  onNavigate: (href: string) => void;
  /** Custom render for each pagination entry */
  itemComponent?: (props: {
    item: TurPaginationItem;
    isCurrent: boolean;
    isEllipsis: boolean;
    onClick: () => void;
    label: string;
  }) => ReactNode;
  className?: string;
}

function camelize(text: string): string {
  return text
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(" ");
}

/**
 * Pagination component that renders Turing pagination items.
 *
 * @since 2026.2.0
 */
export function TuringPagination({
  items,
  onNavigate,
  itemComponent,
  className,
}: TuringPaginationProps) {
  if (!items.length) return null;

  return (
    <nav className={className} aria-label="Pagination">
      {items.map((item, i) => {
        const isCurrent = item.type === "CURRENT";
        const isEllipsis = item.type === "ELLIPSIS" || item.text === "...";
        const label = camelize(item.text);
        const onClick = () => {
          if (!isCurrent && !isEllipsis && item.href) onNavigate(item.href);
        };

        if (itemComponent) {
          return (
            <span key={`${item.type}-${item.page}-${i}`}>
              {itemComponent({ item, isCurrent, isEllipsis, onClick, label })}
            </span>
          );
        }

        if (isEllipsis) {
          return <span key={`ellipsis-${i}`} aria-hidden="true">&hellip;</span>;
        }

        return (
          <button
            key={`${item.type}-${item.page}-${i}`}
            type="button"
            onClick={onClick}
            disabled={isCurrent}
            aria-current={isCurrent ? "page" : undefined}
            aria-label={`Page ${item.text}`}
          >
            {label}
          </button>
        );
      })}
    </nav>
  );
}
