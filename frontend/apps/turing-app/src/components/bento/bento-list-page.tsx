import { LoadProvider } from "@/components/loading-provider";
import { Icon } from "@iconify/react";
import type { Icon as TablerIcon } from "@tabler/icons-react";
import { IconPlus, IconSparkles } from "@tabler/icons-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { BentoHero } from "./bento-hero";
import { BENTO_TONE_GRADIENTS, type BentoTone } from "./bento-tones";

export interface BentoListPageProps<T> {
  /** Query data — `undefined` while loading (drives the LoadProvider gate). */
  items: T[] | undefined;
  /** Connection error message, or null. */
  error?: string | null;
  /** URL the LoadProvider "try again" link points at (usually the list route). */
  tryAgainUrl: string;

  // --- hero ---
  eyebrow: ReactNode;
  heroIcon: TablerIcon;
  title: string;
  subtitle: string;
  /** Tone driving the hero chip + New-tile gradient. Default `indigo`. */
  tone?: BentoTone;

  // --- "new" tile ---
  newRoute: string;
  newLabel: ReactNode;
  newSubtitle?: ReactNode;

  // --- items ---
  itemKey: (item: T) => string;
  /** Render one item's tile. `featured` = the first item (2×2 hero tile). */
  renderTile: (item: T, featured: boolean) => ReactNode;

  // --- empty state ---
  emptyTitle: string;
  emptyDescription: string;
}

/**
 * The reusable Bento list surface (T548) — the `bento-grid` mosaic with a
 * dashed "New" tile, featured-first tiling (item 0 renders 2×2), and an
 * empty-state hint. Every entity list is this component plus a `renderTile`;
 * the common tile shape itself is covered by {@link BentoEntityTile}.
 */
export function BentoListPage<T>({
  items,
  error,
  tryAgainUrl,
  eyebrow,
  heroIcon: HeroIcon,
  title,
  subtitle,
  tone = "indigo",
  newRoute,
  newLabel,
  newSubtitle,
  itemKey,
  renderTile,
  emptyTitle,
  emptyDescription,
}: Readonly<BentoListPageProps<T>>) {
  const gradient = BENTO_TONE_GRADIENTS[tone];

  return (
    <LoadProvider checkIsNotUndefined={items} error={error ?? null} tryAgainUrl={tryAgainUrl}>
      <BentoHero
        eyebrow={eyebrow}
        leading={
          <span className={`grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br ${gradient} text-white shadow-md`}>
            <HeroIcon size={24} />
          </span>
        }
        title={title}
        subtitle={subtitle}
      />

      <div className="bento-grid grid auto-rows-[minmax(140px,auto)] grid-cols-2 gap-4 md:grid-cols-4 md:gap-5 lg:grid-cols-6">
        <NewTile route={newRoute} label={newLabel} subtitle={newSubtitle} gradient={gradient} />
        {(items ?? []).map((item, idx) => (
          <span key={itemKey(item)} className="contents">
            {renderTile(item, idx === 0)}
          </span>
        ))}
        {items?.length === 0 && <EmptyHintTile title={emptyTitle} description={emptyDescription} />}
      </div>
    </LoadProvider>
  );
}

function NewTile({
  route,
  label,
  subtitle,
  gradient,
}: Readonly<{ route: string; label: ReactNode; subtitle?: ReactNode; gradient: string }>) {
  const { t } = useTranslation();
  return (
    /*
     * Visually distinct from content tiles: half the size (2×1 vs the
     * featured's 2×2), no tonal ornament, and a dashed border — the
     * "draft / awaiting input" look that universally reads as "create".
     */
    <Link
      to={route}
      className="bento-tile bento-tile-clickable col-span-2 row-span-1 flex flex-col gap-3 rounded-3xl border-2 border-dashed border-border/60 bg-card/30 p-5 backdrop-blur-md hover:border-indigo-500/60 hover:bg-card/50 md:col-span-2 md:row-span-1 lg:col-span-2 lg:row-span-1"
    >
      <div className="flex items-center gap-3">
        <span className={`bento-pulse grid h-10 w-10 place-items-center rounded-2xl bg-linear-to-br ${gradient} text-white shadow-md`}>
          <IconPlus size={20} />
        </span>
        <span className="text-[11px] uppercase tracking-wider text-muted-foreground">{t("forms.formActions.new", { defaultValue: "New" })}</span>
      </div>
      <div>
        <div className="text-base font-semibold tracking-tight md:text-lg">
          <span className={`bg-linear-to-br ${gradient} bg-clip-text text-transparent`}>{label}</span>
        </div>
        {subtitle && <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>}
      </div>
    </Link>
  );
}

function EmptyHintTile({ title, description }: Readonly<{ title: string; description: string }>) {
  return (
    <div className="bento-tile bento-glass col-span-2 row-span-2 flex flex-col items-start justify-end p-5 md:col-span-4">
      <IconSparkles size={20} className="mb-3 text-indigo-500" />
      <div className="text-lg font-semibold tracking-tight">{title}</div>
      <p className="mt-1 max-w-md text-sm text-muted-foreground">{description}</p>
    </div>
  );
}

export interface BentoEntityTileProps {
  /** Link target for the tile. */
  to: string;
  /** First item renders 2×2 with ambient blobs; the rest render 2×1. */
  featured: boolean;
  /** Default Tabler icon when no Iconify `icon` is set. */
  defaultIcon: TablerIcon;
  /** User-picked Iconify icon name, if any. */
  icon?: string | null;
  tone?: BentoTone;
  title: string;
  description?: string | null;
  /** Optional meta chips/text under the title (e.g. vendor, model name). */
  meta?: ReactNode;
  /** Render the Active/Idle status pill (driven by `enabled`). */
  enabled?: number;
  hasStatus?: boolean;
  /** iOS shared-element transition name (matches the detail page). */
  viewTransitionName?: string;
}

/**
 * The common Bento content tile: tonal icon chip + optional status pill +
 * title + description + optional meta row. Covers the shape shared by the
 * LLM and AI-Agent lists; entities with a bespoke tile can skip it and pass
 * their own node to {@link BentoListPage}'s `renderTile`.
 */
export function BentoEntityTile({
  to,
  featured,
  defaultIcon: DefaultIcon,
  icon,
  tone = "indigo",
  title,
  description,
  meta,
  enabled,
  hasStatus = false,
  viewTransitionName,
}: Readonly<BentoEntityTileProps>) {
  const gradient = BENTO_TONE_GRADIENTS[tone];
  const span = featured
    ? "col-span-2 row-span-2 md:col-span-2 md:row-span-2 lg:col-span-2 lg:row-span-2"
    : "col-span-2 row-span-1 md:col-span-2 md:row-span-1 lg:col-span-2 lg:row-span-1";
  const isEnabled = enabled === 1;
  const padding = featured ? "p-6 md:p-7" : "p-5";
  const iconChip = featured ? "h-14 w-14" : "h-9 w-9";
  const iconSize = featured ? 28 : 18;
  const titleSize = featured ? "text-xl md:text-2xl" : "text-base md:text-lg";

  return (
    <Link
      to={to}
      className={`bento-tile bento-tile-clickable bento-glass group relative flex flex-col ${featured ? "gap-5" : "gap-4"} overflow-hidden ${padding} ${span}`}
      style={viewTransitionName ? ({ viewTransitionName } as React.CSSProperties) : undefined}
    >
      {featured && (
        <>
          <div aria-hidden className={`pointer-events-none absolute -bottom-10 -right-10 h-64 w-64 rounded-full bg-linear-to-br ${gradient} opacity-40 blur-2xl dark:opacity-50`} />
          <div aria-hidden className={`pointer-events-none absolute right-12 top-1/3 h-32 w-32 rounded-full bg-linear-to-tr ${gradient} opacity-20 blur-2xl dark:opacity-30`} />
        </>
      )}

      <div className="relative z-1 flex items-center justify-between">
        <span className={`grid place-items-center rounded-2xl bg-linear-to-br ${gradient} text-white shadow-md ${iconChip}`}>
          {icon
            ? <Icon icon={icon} className={featured ? "size-7 text-white" : "size-4.5 text-white"} />
            : <DefaultIcon size={iconSize} />}
        </span>
        {hasStatus && (
          <span className={`flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[10px] uppercase tracking-wider ${
            isEnabled
              ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
              : "border-border bg-muted text-muted-foreground"
          }`}>
            <span className={`h-1.5 w-1.5 rounded-full ${isEnabled ? "bg-emerald-500 bento-pulse" : "bg-muted-foreground/60"}`} />
            {isEnabled ? "Active" : "Idle"}
          </span>
        )}
      </div>
      <div className="relative z-1">
        <div className={`font-semibold tracking-tight ${titleSize}`}>{title}</div>
        {meta && <div className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">{meta}</div>}
        {description && <p className="mt-2 text-sm text-muted-foreground">{description}</p>}
      </div>
    </Link>
  );
}
