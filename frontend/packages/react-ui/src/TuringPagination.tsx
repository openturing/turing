import type { ReactNode } from "react";

/**
 * Minimal structural shape of a Turing pagination entry — redeclared locally so
 * this package stays zero-runtime-dependency (it cannot import the core
 * `@viglet/turing-sdk` types without breaking viglet.com's `file:` install).
 * Structurally compatible with the SDK's `TurPaginationItem`, so the SDK can
 * re-export this component with no consumer churn.
 */
export interface TuringPaginationItemData {
  href: string;
  page: number;
  text: string;
  type: string;
}

export interface TuringPaginationProps {
  items: TuringPaginationItemData[];
  onNavigate: (href: string) => void;
  /** Custom render for each pagination entry. */
  itemComponent?: (props: {
    item: TuringPaginationItemData;
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
 * Headless pagination — renders Turing pagination items as a nav of buttons,
 * with a render-prop (`itemComponent`) escape hatch and ellipsis handling.
 *
 * <p>Pure renderer: data + callbacks via props, no hook, no API/axios coupling,
 * no baked-in styling. Migrated from {@code @viglet/turing-react-sdk} (T306);
 * the SDK keeps re-exporting it so existing imports are unchanged.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export function TuringPagination({
  items,
  onNavigate,
  itemComponent,
  className,
}: Readonly<TuringPaginationProps>) {
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

TuringPagination.displayName = "TuringPagination";
