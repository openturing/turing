import type { ReactNode } from "react";

export interface BentoHeroProps {
  /**
   * Small label above the headline. Typically the parent section
   * name (acts as a breadcrumb back-link). Accepts a `ReactNode` so
   * consumers can pass a `<Link>` to make it clickable.
   */
  eyebrow?: ReactNode;
  /** Headline — a greeting, the page title, etc. */
  title: ReactNode;
  /** Sub-line below the title. */
  subtitle?: ReactNode;
  /** Visual badge / logo on the left of the headline. */
  leading?: ReactNode;
  /** Trailing slot — actions, chips, etc. Pushed to the far right. */
  trailing?: ReactNode;
}

/**
 * Page-level hero block used at the top of every Bento page.
 *
 * Mirrors the "navigation title" pattern from iOS — large, airy,
 * left-aligned by default, and animates in with the same spring as
 * the rest of the bento shell. Stays outside the bento grid (no
 * frosted-glass surface) so it reads as a section header rather
 * than as a tile.
 *
 * Layout contract: leading + title block always anchor to the start;
 * the optional `trailing` slot is pushed to the far right via
 * `ml-auto` so single-child (no trailing) renders deterministically
 * left-aligned without depending on `justify-content` quirks.
 */
export function BentoHero({
  eyebrow,
  title,
  subtitle,
  leading,
  trailing,
}: Readonly<BentoHeroProps>) {
  return (
    <header className="bento-shell-header mb-6 flex flex-col items-start gap-3 md:mb-10">
      <div className="flex w-full items-start gap-4">
        {leading && <div className="shrink-0">{leading}</div>}
        <div className="flex flex-1 flex-col gap-1">
          {eyebrow && (
            <span className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
              {eyebrow}
            </span>
          )}
          <h1 className="text-3xl font-semibold tracking-tight md:text-4xl">{title}</h1>
          {subtitle && (
            <p className="max-w-2xl text-sm text-muted-foreground">{subtitle}</p>
          )}
        </div>
        {trailing && <div className="ml-auto flex shrink-0 items-center gap-2">{trailing}</div>}
      </div>
    </header>
  );
}
