import { IconChevronRight } from "@tabler/icons-react";
import type { ComponentType, ReactNode } from "react";
import { Link } from "react-router-dom";
import { BENTO_TONE_GRADIENTS, type BentoTone } from "./bento-tones";

export interface BentoTileProps {
  /** Destination route. When omitted, the tile renders as a non-clickable surface. */
  to?: string;
  icon: ComponentType<{ size?: number }>;
  tone: BentoTone;
  eyebrow: string;
  title: string;
  /** Tailwind grid-span classes — the consumer drives the mosaic layout. */
  span: string;
  /**
   * Marks this tile as the "hero" of its section (typically a 2x2 span).
   * Featured tiles get a larger icon, larger title, and a soft tonal
   * ornament that fills the extra real-estate so the surface doesn't
   * read as half-empty next to short-content siblings.
   */
  featured?: boolean;
  children?: ReactNode;
}

/**
 * Generic Bento tile: icon chip + eyebrow + title + optional body.
 * Press / hover / entry animations come from `bento.styles.css` via
 * the `.bento-tile` and `.bento-glass` classes.
 */
export function BentoTile({
  to,
  icon: Icon,
  tone,
  eyebrow,
  title,
  span,
  featured = false,
  children,
}: Readonly<BentoTileProps>) {
  const iconChipClass = featured
    ? "h-14 w-14 rounded-2xl"
    : "h-9 w-9 rounded-2xl";
  const iconSize = featured ? 28 : 18;
  const titleClass = featured
    ? "text-xl font-semibold tracking-tight md:text-2xl"
    : "text-base font-semibold tracking-tight md:text-lg";

  const content = (
    <>
      {/* Tonal ornament for featured tiles — two layered gradient orbs
          fill the extra real-estate that a stretched 2x2 inevitably has
          when sibling rows are tall. The blur + opacity stack reads as
          ambient depth (think SF Symbols hero artwork) rather than as
          a hard graphic, so it stays subtle in light mode and richer
          in dark mode where the gradient catches more contrast. */}
      {featured && (
        <>
          <div
            aria-hidden
            className={`pointer-events-none absolute -bottom-10 -right-10 h-64 w-64 rounded-full bg-linear-to-br ${BENTO_TONE_GRADIENTS[tone]} opacity-40 blur-2xl dark:opacity-50`}
          />
          <div
            aria-hidden
            className={`pointer-events-none absolute right-12 top-1/3 h-32 w-32 rounded-full bg-linear-to-tr ${BENTO_TONE_GRADIENTS[tone]} opacity-20 blur-2xl dark:opacity-30`}
          />
        </>
      )}

      <div className="relative z-1 flex items-center justify-between">
        <span className={`grid place-items-center bg-linear-to-br ${BENTO_TONE_GRADIENTS[tone]} text-white shadow-md ${iconChipClass}`}>
          <Icon size={iconSize} />
        </span>
        {to && (
          <IconChevronRight
            size={featured ? 18 : 16}
            className="text-muted-foreground transition-transform duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] group-hover:translate-x-1"
          />
        )}
      </div>
      <div className="relative z-1">
        <div className="text-[11px] uppercase tracking-wider text-muted-foreground">{eyebrow}</div>
        <div className={`mt-0.5 ${titleClass}`}>{title}</div>
        {children && <div className="mt-2">{children}</div>}
      </div>
    </>
  );

  /*
   * Always stack content from the top.
   *
   * Originally non-featured tiles used `justify-between` (icon top,
   * title-block bottom), which only looked right when each tile filled
   * its 140px row exactly. With `auto-rows-[minmax(140px,auto)]`,
   * sibling tiles can grow the row, stretching shorter tiles and
   * exposing a dead band between icon and title. Top-stacking keeps
   * the icon → title → description rhythm contiguous regardless of
   * how tall the row ends up — empty space, when it exists, lands at
   * the bottom (and on featured tiles, the tonal ornament fills it).
   */
  const className = `bento-tile bento-glass group relative flex flex-col ${featured ? "gap-5" : "gap-4"} overflow-hidden ${featured ? "p-6 md:p-7" : "p-5"} ${span}${to ? " bento-tile-clickable" : ""}`;

  return to
    ? <Link to={to} className={className}>{content}</Link>
    : <div className={className}>{content}</div>;
}
