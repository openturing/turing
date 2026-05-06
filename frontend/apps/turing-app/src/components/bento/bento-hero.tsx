import type { ReactNode } from "react";

export interface BentoHeroProps {
  /** Optional small label above the headline (e.g. "Bento", "Welcome"). */
  eyebrow?: string;
  /** Headline — a greeting, the page title, etc. */
  title: ReactNode;
  /** Sub-line below the title. */
  subtitle?: ReactNode;
  /** Visual badge / logo on the left of the headline. */
  leading?: ReactNode;
  /** Trailing slot — actions, chips, etc. */
  trailing?: ReactNode;
}

/**
 * Page-level hero block used at the top of Bento pages. Mirrors the
 * "navigation title" pattern from iOS — large, airy, and animates in
 * with the same spring as the rest of the bento shell.
 *
 * Stays outside the bento grid (no frosted-glass surface) so it reads
 * as a section header rather than as a tile.
 */
export function BentoHero({
  eyebrow,
  title,
  subtitle,
  leading,
  trailing,
}: Readonly<BentoHeroProps>) {
  return (
    <header className="bento-shell-header mb-6 flex flex-col gap-3 md:mb-10 md:flex-row md:items-end md:justify-between">
      <div className="flex items-center gap-4">
        {leading && <div className="shrink-0">{leading}</div>}
        <div className="flex flex-col gap-1">
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
      </div>
      {trailing && <div className="flex items-center gap-2">{trailing}</div>}
    </header>
  );
}
