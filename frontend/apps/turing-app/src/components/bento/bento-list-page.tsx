import { useBentoLayout, useResetBentoLayout, useSaveBentoLayout, useSaveBentoLayoutGlobal } from "@/api/queries/bento-layout.queries";
import { LoadProvider } from "@/components/loading-provider";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  closestCenter,
  DndContext,
  type DragEndEvent,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import { arrayMove, SortableContext, rectSortingStrategy, useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Icon } from "@iconify/react";
import type { Icon as TablerIcon } from "@tabler/icons-react";
import {
  IconAdjustmentsHorizontal,
  IconArrowBackUp,
  IconDeviceFloppy,
  IconGripVertical,
  IconPlus,
  IconSparkles,
  IconUsers,
  IconX,
} from "@tabler/icons-react";
import { useMemo, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { BentoEmptyState } from "./bento-empty-state";
import { BentoHero } from "./bento-hero";
import {
  BENTO_EMPHASIS_SPAN,
  type BentoEmphasis,
  resolveBentoLayout,
  type ResolvedBentoItem,
  toBentoLayoutEntries,
} from "./bento-layout";
import { BENTO_TONE_GRADIENTS, type BentoTone } from "./bento-tones";

export interface BentoListPageProps<T> {
  /** Query data — `undefined` while loading (drives the LoadProvider gate). */
  items: T[] | undefined;
  /** Connection error message, or null. */
  error?: string | null;
  /** URL the LoadProvider "try again" link points at (usually the list route). */
  tryAgainUrl: string;

  /**
   * Stable surface key (e.g. `"llm"`, `"aiAgent"`). When set, the list becomes
   * **customizable** (T575): a "Customize" affordance lets the user drag-reorder
   * tiles and set per-tile emphasis, persisted via the T574 cascade. Omit for a
   * fixed (featured=idx0) list.
   */
  listId?: string;

  // --- hero ---
  /**
   * Bespoke eyebrow content. For the common "breadcrumb back to a parent"
   * case prefer {@link backTo} / {@link backLabel}, which render the canonical
   * arrow+label via BentoHero. Provide at most one of `eyebrow` / `backTo`.
   */
  eyebrow?: ReactNode;
  /** Canonical breadcrumb back-link target (forwarded to BentoHero). */
  backTo?: string;
  /** Label for the {@link backTo} back-link. */
  backLabel?: ReactNode;
  heroIcon: TablerIcon;
  title: string;
  subtitle: string;
  /** Tone driving the hero chip + New-tile gradient. Default `indigo`. */
  tone?: BentoTone;
  /**
   * Optional global action(s) rendered in a row below the hero (left-aligned,
   * sharing the row with the "Customize" button). Use for list-scoped launch
   * actions that aren't per-item — e.g. the persona↔persona dialogue (T585).
   */
  headerAction?: ReactNode;

  // --- "new" tile ---
  newRoute: string;
  newLabel: ReactNode;
  newSubtitle?: ReactNode;
  /** Hide the dashed "New" tile — e.g. a read-only BYO-infra catalog. */
  hideNew?: boolean;

  // --- items ---
  itemKey: (item: T) => string;
  /** Render one item's tile at the resolved {@link BentoEmphasis} size. */
  renderTile: (item: T, emphasis: BentoEmphasis) => ReactNode;

  // --- empty state ---
  emptyTitle: string;
  emptyDescription: string;
}

const BENTO_GRID_CLASS =
  "bento-grid grid auto-rows-[minmax(140px,auto)] grid-cols-2 gap-4 md:grid-cols-4 md:gap-5 lg:grid-cols-6";

/**
 * The reusable Bento list surface (T548) — the `bento-grid` mosaic with a
 * dashed "New" tile, an empty-state hint, and per-item {@link BentoEmphasis}
 * sizing. When a `listId` is supplied it also gains a drag-reorder + emphasis
 * **edit mode** (T575) whose layout persists through the T574 cascade
 * (per-user override → admin global template → featured=idx0 default). Every
 * entity list is this component plus a `renderTile`; the common tile shape is
 * covered by {@link BentoEntityTile}.
 */
export function BentoListPage<T>({
  items,
  error,
  tryAgainUrl,
  listId,
  eyebrow,
  backTo,
  backLabel,
  heroIcon: HeroIcon,
  title,
  subtitle,
  tone = "indigo",
  headerAction,
  newRoute,
  newLabel,
  newSubtitle,
  hideNew = false,
  itemKey,
  renderTile,
  emptyTitle,
  emptyDescription,
}: Readonly<BentoListPageProps<T>>) {
  const { t } = useTranslation();
  const gradient = BENTO_TONE_GRADIENTS[tone];
  const [editing, setEditing] = useState(false);

  const { data: layout } = useBentoLayout(editing || listId ? listId : undefined);
  const resolved = useMemo(
    () => resolveBentoLayout(items ?? [], itemKey, layout?.entries),
    [items, itemKey, layout?.entries],
  );

  const canCustomize = Boolean(listId) && (items?.length ?? 0) > 0;

  return (
    <LoadProvider checkIsNotUndefined={items} error={error ?? null} tryAgainUrl={tryAgainUrl}>
      <BentoHero
        eyebrow={eyebrow}
        backTo={backTo}
        backLabel={backLabel}
        leading={
          <span className={`grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br ${gradient} text-white shadow-md`}>
            <HeroIcon size={24} />
          </span>
        }
        title={title}
        subtitle={subtitle}
      />

      {(headerAction || (canCustomize && !editing)) && (
        <div className="mb-4 flex flex-wrap items-center gap-2">
          {headerAction}
          {canCustomize && !editing && (
            <Button variant="outline" size="sm" onClick={() => setEditing(true)} className="ml-auto gap-2">
              <IconAdjustmentsHorizontal size={16} />
              {t("bento.layout.customize", { defaultValue: "Customize layout" })}
            </Button>
          )}
        </div>
      )}

      {editing && listId ? (
        <BentoListEditor
          listId={listId}
          resolved={resolved}
          canEditGlobal={layout?.canEditGlobal ?? false}
          isCustomized={layout?.source === "USER"}
          renderTile={renderTile}
          gradient={gradient}
          hideNew={hideNew}
          newRoute={newRoute}
          newLabel={newLabel}
          newSubtitle={newSubtitle}
          onClose={() => setEditing(false)}
        />
      ) : (
        <StaticGrid
          resolved={resolved}
          renderTile={renderTile}
          gradient={gradient}
          hideNew={hideNew}
          newRoute={newRoute}
          newLabel={newLabel}
          newSubtitle={newSubtitle}
          isEmpty={items?.length === 0}
          emptyTitle={emptyTitle}
          emptyDescription={emptyDescription}
        />
      )}
    </LoadProvider>
  );
}

export interface BentoTileGridProps<T> {
  /** Query data — `undefined` while loading (drives the LoadProvider gate). */
  items: T[] | undefined;
  /** Connection error message, or null. */
  error?: string | null;
  /** URL the LoadProvider "try again" link points at. */
  tryAgainUrl: string;
  /** Tone driving the dashed New-tile gradient. Default `indigo`. */
  tone?: BentoTone;
  // --- "new" tile (omit both, or set hideNew, to drop it) ---
  newRoute?: string;
  newLabel?: ReactNode;
  newSubtitle?: ReactNode;
  /** Force-hide the dashed "New" tile (e.g. a Keycloak-backed read-only list). */
  hideNew?: boolean;
  // --- items ---
  itemKey: (item: T) => string;
  /** Render one item's tile (typically a {@link BentoEntityTile}). */
  renderTile: (item: T) => ReactNode;
  // --- empty state ---
  emptyTitle: string;
  emptyDescription: string;
}

/**
 * The Bento mosaic **without** a hero — the frosted `bento-grid` of tiles, an
 * optional dashed "New" tile, and an empty-state hint. Use it for list content
 * that renders inside a shell which already owns the header (e.g. a page with a
 * shared {@link BentoHero} + pill tab-bar `Outlet`, like Administration, or a
 * read-only landing that renders its own hero above the grid). Standalone CRUD
 * lists should use {@link BentoListPage}, which wraps this shape with a hero and
 * the T575 customize/reorder mode.
 */
export function BentoTileGrid<T>({
  items,
  error,
  tryAgainUrl,
  tone = "indigo",
  newRoute,
  newLabel,
  newSubtitle,
  hideNew = false,
  itemKey,
  renderTile,
  emptyTitle,
  emptyDescription,
}: Readonly<BentoTileGridProps<T>>) {
  const gradient = BENTO_TONE_GRADIENTS[tone];
  const newTile =
    !hideNew && newRoute && newLabel ? (
      <NewTile route={newRoute} label={newLabel} subtitle={newSubtitle} gradient={gradient} />
    ) : null;

  return (
    <LoadProvider checkIsNotUndefined={items} error={error ?? null} tryAgainUrl={tryAgainUrl}>
      <div className={BENTO_GRID_CLASS}>
        {newTile}
        {(items ?? []).map((item) => (
          <span key={itemKey(item)} className="contents">
            {renderTile(item)}
          </span>
        ))}
        {items?.length === 0 && <EmptyHintTile title={emptyTitle} description={emptyDescription} />}
      </div>
    </LoadProvider>
  );
}

function StaticGrid<T>({
  resolved,
  renderTile,
  gradient,
  hideNew,
  newRoute,
  newLabel,
  newSubtitle,
  isEmpty,
  emptyTitle,
  emptyDescription,
}: Readonly<{
  resolved: ResolvedBentoItem<T>[];
  renderTile: (item: T, emphasis: BentoEmphasis) => ReactNode;
  gradient: string;
  hideNew: boolean;
  newRoute: string;
  newLabel: ReactNode;
  newSubtitle?: ReactNode;
  isEmpty: boolean;
  emptyTitle: string;
  emptyDescription: string;
}>) {
  const hasSmall = resolved.some((r) => r.emphasis === "SMALL");
  return (
    <div className={cn(BENTO_GRID_CLASS, hasSmall && "grid-flow-dense")}>
      {!hideNew && <NewTile route={newRoute} label={newLabel} subtitle={newSubtitle} gradient={gradient} />}
      {resolved.map(({ item, key, emphasis }) => (
        <span key={key} className="contents">
          {renderTile(item, emphasis)}
        </span>
      ))}
      {isEmpty && <EmptyHintTile title={emptyTitle} description={emptyDescription} />}
    </div>
  );
}

/**
 * The drag-reorder + emphasis editor (T575). Drag changes **order only**
 * (`@dnd-kit`, with a keyboard sensor for the T550 a11y baseline); a per-tile
 * button cycles the size. Saving writes the current user's protective override;
 * an admin may also save it as the global template; reset clears the override.
 */
function BentoListEditor<T>({
  listId,
  resolved,
  canEditGlobal,
  isCustomized,
  renderTile,
  gradient,
  hideNew,
  newRoute,
  newLabel,
  newSubtitle,
  onClose,
}: Readonly<{
  listId: string;
  resolved: ResolvedBentoItem<T>[];
  canEditGlobal: boolean;
  isCustomized: boolean;
  renderTile: (item: T, emphasis: BentoEmphasis) => ReactNode;
  gradient: string;
  hideNew: boolean;
  newRoute: string;
  newLabel: ReactNode;
  newSubtitle?: ReactNode;
  onClose: () => void;
}>) {
  const { t } = useTranslation();
  const [draft, setDraft] = useState<ResolvedBentoItem<T>[]>(resolved);

  const saveUser = useSaveBentoLayout();
  const saveGlobal = useSaveBentoLayoutGlobal();
  const reset = useResetBentoLayout();
  const busy = saveUser.isPending || saveGlobal.isPending || reset.isPending;

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor),
  );

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    setDraft((prev) => {
      const from = prev.findIndex((r) => r.key === active.id);
      const to = prev.findIndex((r) => r.key === over.id);
      return from < 0 || to < 0 ? prev : arrayMove(prev, from, to);
    });
  }

  function setEmphasis(key: string, emphasis: BentoEmphasis) {
    setDraft((prev) => prev.map((r) => (r.key === key ? { ...r, emphasis } : r)));
  }

  const entries = useMemo(() => toBentoLayoutEntries(draft), [draft]);
  const hasSmall = draft.some((r) => r.emphasis === "SMALL");

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center gap-2 rounded-2xl border border-border/60 bg-card/40 p-3 backdrop-blur-md">
        <span className="mr-auto text-sm text-muted-foreground">
          {t("bento.layout.editHint", { defaultValue: "Drag tiles to reorder · tap the size button to resize" })}
        </span>
        {isCustomized && (
          <Button
            variant="ghost"
            size="sm"
            className="gap-2"
            disabled={busy}
            onClick={() => reset.mutate(listId, { onSuccess: onClose })}
          >
            <IconArrowBackUp size={16} />
            {t("bento.layout.reset", { defaultValue: "Reset to default" })}
          </Button>
        )}
        {canEditGlobal && (
          <Button
            variant="outline"
            size="sm"
            className="gap-2"
            disabled={busy}
            onClick={() => saveGlobal.mutate({ listId, entries }, { onSuccess: onClose })}
          >
            <IconUsers size={16} />
            {t("bento.layout.saveGlobal", { defaultValue: "Set as default for everyone" })}
          </Button>
        )}
        <Button variant="ghost" size="sm" className="gap-2" disabled={busy} onClick={onClose}>
          <IconX size={16} />
          {t("common.cancel", { defaultValue: "Cancel" })}
        </Button>
        <Button
          size="sm"
          className="gap-2"
          disabled={busy}
          onClick={() => saveUser.mutate({ listId, entries }, { onSuccess: onClose })}
        >
          <IconDeviceFloppy size={16} />
          {t("bento.layout.save", { defaultValue: "Save layout" })}
        </Button>
      </div>

      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={draft.map((r) => r.key)} strategy={rectSortingStrategy}>
          <div className={cn(BENTO_GRID_CLASS, hasSmall && "grid-flow-dense")}>
            {/* The New tile stays in place (non-draggable) so the editable grid
                is WYSIWYG with the saved result — same tile, same slot, same
                packing. It just isn't a sortable item. */}
            {!hideNew && <NewTile route={newRoute} label={newLabel} subtitle={newSubtitle} gradient={gradient} />}
            {draft.map(({ item, key, emphasis }) => (
              <BentoSortableTile
                key={key}
                id={key}
                emphasis={emphasis}
                onSetEmphasis={(next) => setEmphasis(key, next)}
              >
                {renderTile(item, emphasis)}
              </BentoSortableTile>
            ))}
          </div>
        </SortableContext>
      </DndContext>
    </div>
  );
}

const EMPHASIS_ORDER: readonly BentoEmphasis[] = ["SMALL", "MEDIUM", "LARGE"];
const EMPHASIS_LABEL_KEY: Record<BentoEmphasis, { key: string; def: string }> = {
  SMALL: { key: "bento.layout.size.small", def: "Small" },
  MEDIUM: { key: "bento.layout.size.medium", def: "Medium" },
  LARGE: { key: "bento.layout.size.large", def: "Large" },
};

function BentoSortableTile({
  id,
  emphasis,
  onSetEmphasis,
  children,
}: Readonly<{
  id: string;
  emphasis: BentoEmphasis;
  onSetEmphasis: (emphasis: BentoEmphasis) => void;
  children: ReactNode;
}>) {
  const { t } = useTranslation();
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id });
  const style: React.CSSProperties = {
    transform: CSS.Translate.toString(transform),
    transition,
    opacity: isDragging ? 0.6 : 1,
    zIndex: isDragging ? 50 : undefined,
  };
  return (
    <div ref={setNodeRef} style={style} className={cn("relative h-full", BENTO_EMPHASIS_SPAN[emphasis])}>
      {/* The tile itself is inert while editing — its own col-span classes are
          no-ops here (this wrapper is not a grid), and pointer events are off so
          a drag/click never navigates. The wrapper owns the grid span. */}
      <div className="pointer-events-none h-full select-none [&>*]:h-full [&>*]:w-full">{children}</div>
      <div className="absolute right-2 top-2 z-10 flex items-center gap-1">
        {/* Segmented size picker — one click to any size, no cycling. */}
        <div
          role="group"
          aria-label={t("bento.layout.sizeGroup", { defaultValue: "Tile size" })}
          className="flex items-center gap-0.5 rounded-full border border-border/60 bg-background/80 p-0.5 shadow-sm backdrop-blur"
        >
          {EMPHASIS_ORDER.map((size) => {
            const active = size === emphasis;
            const label = t(EMPHASIS_LABEL_KEY[size].key, { defaultValue: EMPHASIS_LABEL_KEY[size].def });
            return (
              <button
                key={size}
                type="button"
                onClick={() => onSetEmphasis(size)}
                aria-pressed={active}
                aria-label={label}
                title={label}
                className={cn(
                  "grid h-6 w-6 place-items-center rounded-full text-[11px] font-semibold uppercase transition-colors",
                  active ? "bg-primary text-primary-foreground shadow" : "text-muted-foreground hover:bg-muted",
                )}
              >
                {label.charAt(0)}
              </button>
            );
          })}
        </div>
        <button
          type="button"
          {...attributes}
          {...listeners}
          aria-label={t("bento.layout.dragToReorder", { defaultValue: "Drag to reorder" })}
          className="grid h-7 w-7 cursor-grab place-items-center rounded-full border border-border/60 bg-background/80 shadow-sm backdrop-blur hover:bg-background active:cursor-grabbing"
        >
          <IconGripVertical size={14} className="text-muted-foreground" />
        </button>
      </div>
    </div>
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
  // Shares the canonical BentoEmptyState look (T572); the grid span keeps it a
  // 2×2 cell in the mosaic, left-aligned to sit beside the "New" tile.
  return (
    <BentoEmptyState
      icon={IconSparkles}
      title={title}
      description={description}
      align="start"
      className="col-span-2 row-span-2 justify-end md:col-span-4"
    />
  );
}

export interface BentoEntityTileProps {
  /** Link target for the tile. */
  to: string;
  /**
   * Tile prominence. `LARGE` = 2×2 hero, `MEDIUM` = 2×1 wide (default),
   * `SMALL` = compact 1×1. Takes precedence over the legacy `featured` flag.
   */
  emphasis?: BentoEmphasis;
  /**
   * Legacy prominence flag (still used by the home + area mosaics): `true` maps
   * to `LARGE`, `false` to `MEDIUM`. Ignored when `emphasis` is set.
   */
  featured?: boolean;
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
 * title + description + optional meta row, sized by {@link BentoEmphasis}.
 * Covers the shape shared by the entity lists; entities with a bespoke tile can
 * skip it and pass their own node to {@link BentoListPage}'s `renderTile`.
 */
export function BentoEntityTile({
  to,
  emphasis,
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
  const { t } = useTranslation();
  const gradient = BENTO_TONE_GRADIENTS[tone];
  const resolvedEmphasis: BentoEmphasis = emphasis ?? (featured ? "LARGE" : "MEDIUM");
  const isLarge = resolvedEmphasis === "LARGE";
  const isSmall = resolvedEmphasis === "SMALL";
  const span = BENTO_EMPHASIS_SPAN[resolvedEmphasis];
  const isEnabled = enabled === 1;
  const padding = isLarge ? "p-6 md:p-7" : isSmall ? "p-4" : "p-5";
  const iconChip = isLarge ? "h-14 w-14" : isSmall ? "h-8 w-8" : "h-9 w-9";
  const iconSize = isLarge ? 28 : isSmall ? 16 : 18;
  const titleSize = isLarge ? "text-xl md:text-2xl" : isSmall ? "text-sm" : "text-base md:text-lg";

  return (
    <Link
      to={to}
      className={`bento-tile bento-tile-clickable bento-glass group relative flex flex-col ${isLarge ? "gap-5" : "gap-4"} overflow-hidden ${padding} ${span}`}
      style={viewTransitionName ? ({ viewTransitionName } as React.CSSProperties) : undefined}
    >
      {isLarge && (
        <>
          <div aria-hidden className={`pointer-events-none absolute -bottom-10 -right-10 h-64 w-64 rounded-full bg-linear-to-br ${gradient} opacity-40 blur-2xl dark:opacity-50`} />
          <div aria-hidden className={`pointer-events-none absolute right-12 top-1/3 h-32 w-32 rounded-full bg-linear-to-tr ${gradient} opacity-20 blur-2xl dark:opacity-30`} />
        </>
      )}

      <div className="relative z-1 flex items-center justify-between">
        <span className={`grid place-items-center rounded-2xl bg-linear-to-br ${gradient} text-white shadow-md ${iconChip}`}>
          {icon
            ? <Icon icon={icon} className={isLarge ? "size-7 text-white" : "size-4.5 text-white"} />
            : <DefaultIcon size={iconSize} />}
        </span>
        {hasStatus && (
          <span className={`flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[10px] uppercase tracking-wider ${
            isEnabled
              ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
              : "border-border bg-muted text-muted-foreground"
          }`}>
            <span className={`h-1.5 w-1.5 rounded-full ${isEnabled ? "bg-emerald-500 bento-pulse" : "bg-muted-foreground/60"}`} />
            {isEnabled ? t("common.active", { defaultValue: "Active" }) : t("common.idle", { defaultValue: "Idle" })}
          </span>
        )}
      </div>
      <div className="relative z-1">
        <div className={`font-semibold tracking-tight ${titleSize}`}>{title}</div>
        {!isSmall && meta && <div className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">{meta}</div>}
        {!isSmall && description && <p className="mt-2 text-sm text-muted-foreground">{description}</p>}
      </div>
    </Link>
  );
}
