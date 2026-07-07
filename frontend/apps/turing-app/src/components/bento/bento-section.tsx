import { IconChevronRight } from "@tabler/icons-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

export interface BentoSectionProps {
  /** Section heading — small uppercase eyebrow above the title. */
  eyebrow?: string;
  title: string;
  description?: string;
  /**
   * When set, the title becomes a link to this route (e.g. the section's area
   * hub) with a chevron affordance. Used on the home overview so each section
   * heading drills into its dedicated hub page.
   */
  titleHref?: string;
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
  titleHref,
  children,
  gridClassName = DEFAULT_GRID_CLASSES,
}: Readonly<BentoSectionProps>) {
  const heading = (
    <h2 className="text-xl font-semibold tracking-tight md:text-2xl">{title}</h2>
  );
  return (
    <section className="mb-8 md:mb-10">
      <header className="bento-shell-header mb-4 flex flex-col gap-0.5">
        {eyebrow && (
          <span className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
            {eyebrow}
          </span>
        )}
        {titleHref ? (
          <Link
            to={titleHref}
            className="group inline-flex w-fit items-center gap-1 text-foreground transition-colors hover:text-primary"
          >
            {heading}
            <IconChevronRight
              size={20}
              className="text-muted-foreground transition-transform duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] group-hover:translate-x-1"
            />
          </Link>
        ) : (
          heading
        )}
        {description && (
          <p className="max-w-2xl text-sm text-muted-foreground">{description}</p>
        )}
      </header>
      <div className={gridClassName}>{children}</div>
    </section>
  );
}
