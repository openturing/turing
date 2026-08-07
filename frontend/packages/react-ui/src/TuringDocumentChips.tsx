import type { ReactNode } from "react";

/**
 * What clicking a chip does to the request's `fq`. Mirrors the SDK's
 * `TurFacetItemAction`, redeclared locally so this package stays
 * zero-runtime-dependency.
 */
export type TuringFacetAction = "ADD" | "REMOVE" | "REPLACE";

/**
 * One of a document's facet chips. Two shapes, because the response has two: the
 * legacy one carries an `href` to navigate, the T864 link-free one carries the
 * `filterQuery` to add to (or subtract from) the request. `text` is common.
 *
 * <p>Structurally compatible with the SDK's `TurDocumentMetadata` and
 * `TurStructuredDocumentMetadata` — `selected` and `action` are optional here
 * because a legacy chip has neither and a pre-T869 server sent neither.
 */
export type TuringDocumentChip = { text: string } & (
  | { href: string }
  | {
      field: string;
      value: string;
      filterQuery: string;
      selected?: boolean;
      action?: TuringFacetAction;
    }
);

/** The subset of a chip that identifies the filter to toggle. */
export interface TuringFacetToggleTarget {
  field: string;
  filterQuery: string;
  action: TuringFacetAction;
}

export interface TuringDocumentChipsProps {
  chips: TuringDocumentChip[];
  /**
   * Navigate a legacy chip's href.
   *
   * @deprecated T882 — the href is the query-string round-trip §XLV removes.
   * It stays because the legacy response is still supported; a POST-native
   * consumer only needs {@link onToggleFacet}.
   */
  onNavigate?: (href: string) => void;
  /**
   * T882 — toggle the chip's filter. **Omit it and every structured chip renders
   * inert**, which is the T867 bug: a badge that looks clickable and is not.
   * Supplying it is what makes a chip an action.
   */
  onToggleFacet?: (target: TuringFacetToggleTarget) => void;
  /** Custom render for one chip; receives everything the default renderer uses. */
  itemComponent?: (props: {
    chip: TuringDocumentChip;
    /** Whether this chip has somewhere to go — a handler *and* something to act on. */
    isActionable: boolean;
    /** T869 — whether the chip's filter is part of the current request. */
    isSelected: boolean;
    onClick?: () => void;
  }) => ReactNode;
  className?: string;
  /** Applied to every chip the default renderer emits. */
  chipClassName?: string;
  /** Appended to {@link chipClassName} on a selected chip. */
  selectedChipClassName?: string;
}

/** A chip identifies itself by its text plus whatever it gives us to act on. */
function chipKey(chip: TuringDocumentChip): string {
  return `${chip.text}-${"href" in chip ? chip.href : chip.filterQuery}`;
}

/**
 * T882 / §LXX.4 — headless renderer for a document's facet chips.
 *
 * <p>`TuringRawDocument.metadata` has carried both chip shapes since T864 and told
 * the consumer to "narrow on `"href" in chip` if you act on it" — while offering
 * nothing to act with. So a viglet.com row component rendered `chip.text` in a
 * badge and stopped, which is exactly the dead badge T867 removed from the admin
 * app. This narrows the union once and turns each arm into the action it already
 * described: an anchor for the legacy href, an `aria-pressed` button for the
 * link-free filter query.
 *
 * <p>Design-agnostic by the same rule as the rest of this package: no SDK import,
 * no baked styling, `itemComponent` for a consumer that wants its own markup.
 * A chip with no handler renders as plain text rather than as a fake control.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function TuringDocumentChips({
  chips,
  onNavigate,
  onToggleFacet,
  itemComponent,
  className,
  chipClassName,
  selectedChipClassName,
}: Readonly<TuringDocumentChipsProps>) {
  if (!chips?.length) return null;

  return (
    <span className={className}>
      {chips.map((chip) => {
        if (!chip.text) return null;

        const isLegacy = "href" in chip;
        const isSelected = !isLegacy && chip.selected === true;
        const isActionable = isLegacy
          ? Boolean(chip.href && onNavigate)
          : Boolean(chip.filterQuery && onToggleFacet);

        const onClick = !isActionable
          ? undefined
          : isLegacy
            ? () => onNavigate!(chip.href)
            : () =>
                onToggleFacet!({
                  field: chip.field,
                  filterQuery: chip.filterQuery,
                  // A pre-T869 server sent no action; the selection state
                  // resolves it the same way the facet rail does.
                  action: chip.action ?? (isSelected ? "REMOVE" : "ADD"),
                });

        if (itemComponent) {
          return (
            <span key={chipKey(chip)}>
              {itemComponent({ chip, isActionable, isSelected, onClick })}
            </span>
          );
        }

        const classes = [chipClassName, isSelected ? selectedChipClassName : null]
          .filter(Boolean)
          .join(" ");

        if (!isActionable) {
          return (
            <span key={chipKey(chip)} className={classes || undefined}>
              {chip.text}
            </span>
          );
        }

        if (isLegacy) {
          return (
            <a
              key={chipKey(chip)}
              href={chip.href}
              className={classes || undefined}
              onClick={(event) => {
                // The href stays on the anchor for deep-linking / SEO / no-JS;
                // the handler is what a JS consumer actually wants.
                event.preventDefault();
                onClick!();
              }}
            >
              {chip.text}
            </a>
          );
        }

        return (
          <button
            key={chipKey(chip)}
            type="button"
            onClick={onClick}
            aria-pressed={isSelected}
            className={classes || undefined}
          >
            {chip.text}
          </button>
        );
      })}
    </span>
  );
}

TuringDocumentChips.displayName = "TuringDocumentChips";
