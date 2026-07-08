import type { Icon as TablerIcon } from "@tabler/icons-react";
import { IconSparkles } from "@tabler/icons-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import { BENTO_TONE_GRADIENTS, type BentoTone } from "./bento-tones";

export interface BentoEmptyStateProps {
  /** Tonal icon in the leading chip. Defaults to a sparkle. */
  icon?: TablerIcon;
  /** Tone driving the icon chip gradient. Default `indigo`. */
  tone?: BentoTone;
  /** Headline — what's missing, in plain language. */
  title: string;
  /** One-line hint on how to fill it. */
  description?: ReactNode;
  /** Optional call-to-action (a `BentoEntityTile`-style link, a button, …). */
  action?: ReactNode;
  /**
   * Content alignment. `center` (default) suits standalone panels — a chart
   * with no data, an analytics tab before the first run. `start` suits an
   * empty cell inside a mosaic (see the list-page `EmptyHintTile`).
   */
  align?: "center" | "start";
  className?: string;
}

/**
 * The one canonical Bento empty-state (T572). Every "nothing here yet" surface
 * — an empty entity list, a chart with no samples, an analytics tab before its
 * first data point — renders this so the void reads the same everywhere: a
 * frosted `bento-glass` panel, a tonal icon chip, a short title, a one-line
 * hint, and an optional CTA. Consistency here is the delight: an empty screen
 * that looks designed rather than broken.
 *
 * The list mosaic's `EmptyHintTile` delegates to this component (wrapping it in
 * the grid span), so lists and standalone panels stay visually identical.
 */
export function BentoEmptyState({
  icon: Icon = IconSparkles,
  tone = "indigo",
  title,
  description,
  action,
  align = "center",
  className,
}: Readonly<BentoEmptyStateProps>) {
  const gradient = BENTO_TONE_GRADIENTS[tone];
  const centered = align === "center";
  return (
    <div
      className={cn(
        "bento-tile bento-glass flex flex-col gap-3 rounded-3xl p-8",
        centered ? "items-center text-center" : "items-start text-left",
        className,
      )}
    >
      <span
        className={`grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br ${gradient} text-white shadow-md`}
      >
        <Icon size={24} />
      </span>
      <div className="text-lg font-semibold tracking-tight">{title}</div>
      {description && (
        <p className={cn("max-w-md text-sm text-muted-foreground", centered && "mx-auto")}>
          {description}
        </p>
      )}
      {action && <div className={cn("mt-1", centered && "mx-auto")}>{action}</div>}
    </div>
  );
}
