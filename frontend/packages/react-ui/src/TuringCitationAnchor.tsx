import type { ComponentPropsWithoutRef, ReactNode } from "react";
import type { Components } from "react-markdown";

/**
 * T836 / §LXII.5 — the clickable footnote anchor that {@link rehypeCitationAnchors}
 * markers render into. Clicking (or activating) it scroll-links to the matching
 * source card — an element whose id is `${idPrefix}${index}` (default
 * `turing-source-1`, `turing-source-2`, …) — and briefly flags it via a
 * `data-turing-citation-target` attribute the host can animate; hovering shows the
 * cited passage. Headless and zero-dep like the rest of the package: skin via
 * `classNames`, or import the token stylesheet for the default look.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

/** Per-slot class names — the component ships no visual styling of its own. */
export interface TuringCitationAnchorClassNames {
  /** The `<sup>` wrapper. */
  anchor?: string;
  /** The clickable button. */
  button?: string;
}

/** Popover/label data for one footnote, keyed by its 1-based {@link index}. */
export interface TuringCitationAnchorData {
  /** 1-based footnote number (agrees with the source chip after T832). */
  index: number;
  /** Source title, used for the accessible label. */
  label?: string;
  /** The cited passage, shown as the hover popover. */
  citedText?: string;
  /** Deep link to the source (unused by the default anchor; for host renderers). */
  url?: string;
}

export interface TuringCitationAnchorProps {
  /** 1-based footnote number. */
  index: number;
  /** Source title for the accessible label. */
  label?: string;
  /** Cited passage shown on hover. */
  citedText?: string;
  /** DOM id prefix of the target source card. Default `turing-source-`. */
  idPrefix?: string;
  /** Called after the default scroll+highlight, with the footnote number. */
  onActivate?: (index: number) => void;
  /** Convenience alias for {@link TuringCitationAnchorClassNames.anchor}. */
  className?: string;
  classNames?: TuringCitationAnchorClassNames;
}

const DEFAULT_ID_PREFIX = "turing-source-";
const HIGHLIGHT_ATTR = "data-turing-citation-target";
const HIGHLIGHT_MS = 1600;

export function TuringCitationAnchor({
  index,
  label,
  citedText,
  idPrefix = DEFAULT_ID_PREFIX,
  onActivate,
  className,
  classNames,
}: Readonly<TuringCitationAnchorProps>) {
  const activate = () => {
    if (typeof document !== "undefined") {
      const target = document.getElementById(`${idPrefix}${index}`);
      if (target) {
        target.scrollIntoView({ behavior: "smooth", block: "nearest" });
        target.setAttribute(HIGHLIGHT_ATTR, "");
        globalThis.setTimeout(() => target.removeAttribute(HIGHLIGHT_ATTR), HIGHLIGHT_MS);
      }
    }
    onActivate?.(index);
  };
  const title = citedText || label || undefined;
  const ariaLabel = label ? `Citation ${index}: ${label}` : `Citation ${index}`;
  return (
    <sup
      className={className ?? classNames?.anchor ?? "turing-citation-anchor"}
      data-turing-citation={index}
    >
      <button
        type="button"
        className={classNames?.button}
        title={title}
        aria-label={ariaLabel}
        onClick={activate}
      >
        [{index}]
      </button>
    </sup>
  );
}

/** Options for {@link citationMarkdownComponents}. */
export interface CitationMarkdownComponentsOptions {
  /** Per-footnote popover/label data (looked up by {@link TuringCitationAnchorData.index}). */
  citations?: ReadonlyArray<TuringCitationAnchorData>;
  /** DOM id prefix of the target source cards. Default `turing-source-`. */
  idPrefix?: string;
  /** Called when an anchor is activated, with the footnote number. */
  onActivate?: (index: number) => void;
  classNames?: TuringCitationAnchorClassNames;
}

/**
 * Builds the `components` map to hand {@link TuringMarkdown} alongside
 * {@link rehypeCitationAnchors}: it renders every `<sup>` the plugin marked with
 * `data-turing-citation` as a {@link TuringCitationAnchor}, and passes any other
 * `<sup>` (a genuine superscript, a GFM footnote ref) straight through unchanged.
 */
export function citationMarkdownComponents(
  options: CitationMarkdownComponentsOptions = {},
): Components {
  const byIndex = new Map<number, TuringCitationAnchorData>();
  for (const citation of options.citations ?? []) {
    byIndex.set(citation.index, citation);
  }
  return {
    sup(props) {
      const { node, children, ...rest } = props as {
        node?: { properties?: Record<string, unknown> };
        children?: ReactNode;
      };
      const raw = node?.properties?.dataTuringCitation;
      if (raw == null) {
        return <sup {...(rest as ComponentPropsWithoutRef<"sup">)}>{children}</sup>;
      }
      const index = Number(raw);
      const data = byIndex.get(index);
      return (
        <TuringCitationAnchor
          index={index}
          label={data?.label}
          citedText={data?.citedText}
          idPrefix={options.idPrefix}
          onActivate={options.onActivate}
          classNames={options.classNames}
        />
      );
    },
  };
}
