import type { ReactNode } from "react";

export interface BentoSectionProps {
  /** Section heading — small uppercase eyebrow above the title. */
  eyebrow?: string;
  title: string;
  description?: string;
  /** Tile children — should be `BentoTile` / `BentoCountTile` instances. */
  children: ReactNode;
  /** Override the grid template. Defaults to a 2/4/6-col responsive bento grid. */
  gridClassName?: string;
}

/*
 * `minmax(140px, auto)` — every row is at least the bento base height
 * (so tiles still have a comfortable square minimum), but rows can grow
 * to fit longer content. CSS grid then sizes spanning tiles (e.g. 2x2)
 * to the sum of the rows they occupy, which keeps the mosaic proportions
 * intact even when descriptions vary in length.
 */
const DEFAULT_GRID_CLASSES =
  "bento-grid grid auto-rows-[minmax(140px,auto)] grid-cols-2 gap-4 md:grid-cols-4 md:gap-5 lg:grid-cols-6";

/**
 * Section wrapper for a Bento page. Renders an iOS-style heading
 * (eyebrow + title + description) followed by a stagger-animated grid.
 *
 * Each section runs its own `bento-grid` so the nth-child stagger
 * restarts within the section — preserves the cascading reveal even
 * across long pages.
 */
export function BentoSection({
  eyebrow,
  title,
  description,
  children,
  gridClassName = DEFAULT_GRID_CLASSES,
}: Readonly<BentoSectionProps>) {
  return (
    <section className="mb-8 md:mb-10">
      <header className="bento-shell-header mb-4 flex flex-col gap-0.5">
        {eyebrow && (
          <span className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
            {eyebrow}
          </span>
        )}
        <h2 className="text-xl font-semibold tracking-tight md:text-2xl">{title}</h2>
        {description && (
          <p className="max-w-2xl text-sm text-muted-foreground">{description}</p>
        )}
      </header>
      <div className={gridClassName}>{children}</div>
    </section>
  );
}
