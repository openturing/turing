import { DialogDelete } from "@/components/dialog.delete";
import { GradientButton } from "@/components/ui/gradient-button";
import type { Icon as TablerIcon } from "@tabler/icons-react";
import { IconDeviceFloppy, IconTrash, IconX } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router-dom";
import { BentoActionsMenu, type BentoActionsMenuItem } from "./bento-actions-menu";
import { BentoHero } from "./bento-hero";
import { BentoHeroIconPicker } from "./bento-hero-icon-picker";
import { BentoInlineEdit } from "./bento-inline-edit";
import type { BentoTone } from "./bento-tones";

/**
 * The identity fields every Bento entity hero owns: title, description,
 * an optional Iconify icon, and an `enabled` status flag (0/1). These
 * are edited in the hero (inline-edit + icon picker + status pill), not
 * in the form body — the form's own FormFields for these are lifted out.
 */
export interface BentoIdentity {
  title: string;
  description: string;
  icon: string | null;
  /** 0 = idle, 1 = active. Only surfaced as a pill when `hasStatus`. */
  enabled: number;
}

/** Form state the render-prop child reports up so the hero can mirror it. */
export interface BentoShellFormState {
  isDirty: boolean;
  isSubmitting: boolean;
}

/** Fields the shell reads off the entity to seed the hero identity. */
type BentoEntityLike = Partial<BentoIdentity> & { id?: string };

export interface BentoEntityShellRenderArgs {
  /** Latest hero identity — merged into the form's create/update payload. */
  staged: BentoIdentity;
  /** Form reports dirty/submitting so the hero renders Save/Cancel correctly. */
  onStateChange: (state: BentoShellFormState) => void;
}

export interface BentoEntityShellProps<TEntity extends BentoEntityLike> {
  /** The entity being edited. In `isNew` mode pass an empty object. */
  entity: TEntity;
  isNew: boolean;
  /** Placeholder shown in the title inline-edit when empty. */
  headlineFallback: string;
  /** Breadcrumb label above the title — usually the section name. */
  eyebrow: ReactNode;
  /** List route the eyebrow links to and Cancel/Delete navigate back to. */
  listRoute: string;
  /** Default hero icon when no Iconify icon is picked. */
  icon: TablerIcon;
  tone: BentoTone;
  /** DOM id of the `<form>` the hero-anchored Save button submits (`form="…"`). */
  formId: string;
  /** i18n'd feature label used in toasts (e.g. `t("llm.title")`). */
  feature: string;
  /** Render the Active/Idle status pill (driven by `enabled`). */
  hasStatus?: boolean;
  /**
   * Existing-mode immediate save. Receives the entity already merged with
   * the changed identity patch. Omit in new mode (fields stage locally and
   * the form's Save creates the entity).
   */
  onUpdate?: (next: TEntity) => Promise<unknown>;
  /** Delete callback; returns whether the delete succeeded. */
  onDelete?: () => Promise<boolean>;
  /** Extra `⋮` menu actions rendered above the built-in Delete. */
  extraActions?: BentoActionsMenuItem[];
  children: (args: BentoEntityShellRenderArgs) => ReactNode;
}

/**
 * The reusable Bento detail scaffold — the lever that makes every remaining
 * CRUD screen a thin config instead of a bespoke ~360-line page (T548).
 *
 * Owns everything the LLM/AI-Agent detail pattern settled on:
 *
 *   - **Identity hero** — `BentoInlineEdit` title/description, a
 *     `BentoHeroIconPicker`, and (optionally) a clickable Active/Idle pill.
 *   - **Two modes** — *existing*: each identity field commits immediately
 *     via `onUpdate` (type → blur → saved); *new*: fields stage locally and
 *     the form's Save creates the entity (no mutation until Save).
 *   - **Scroll-linked hero → sticky-save-bar morph** — a single rAF loop
 *     writes `--bento-fade` on `<html>`; the hero's Save/Cancel
 *     (`bento-fade-out`) and the form's fixed `BentoSaveBar` (`bento-fade-in`)
 *     both interpolate off it with zero React re-renders during scroll.
 *   - **Destructive actions** in a `⋮` `BentoActionsMenu`, never the save bar.
 *
 * The form body is a render prop receiving `{ staged, onStateChange }` and
 * wraps the existing react-hook-form fields in `BentoFormSection`s unchanged.
 */
export function BentoEntityShell<TEntity extends BentoEntityLike>({
  entity,
  isNew,
  headlineFallback,
  eyebrow,
  listRoute,
  icon,
  tone,
  formId,
  feature,
  hasStatus = false,
  onUpdate,
  onDelete,
  extraActions,
  children,
}: Readonly<BentoEntityShellProps<TEntity>>) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [formState, setFormState] = useState<BentoShellFormState>({ isDirty: false, isSubmitting: false });

  /*
   * Scroll-linked "hero-to-sticky-bar morph" progress. Compute a 0..1
   * value from the sentinel's viewport position each scroll frame and
   * write it as `--bento-fade` on `<html>`. CSS inheritance distributes
   * it: the hero buttons (`bento-fade-out`), the form's fixed save bar
   * (`bento-fade-in`) and the layout spacer (`bento-save-bar-spacer`) all
   * interpolate without React state — zero re-renders during scroll.
   *
   *   - sentinel at 160px (STICKY_OFFSET + REVEAL_RANGE) → progress 0
   *   - sentinel at  80px (STICKY_OFFSET)               → progress 1
   *
   * `bento-fade-half` is toggled on `<html>` past 0.5 so the CSS can flip
   * pointer-events between the two element groups at the visual midpoint.
   */
  const heroSentinelRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const node = heroSentinelRef.current;
    if (!node) return;

    const STICKY_OFFSET = 80;
    const REVEAL_RANGE = 80;
    const startTop = STICKY_OFFSET + REVEAL_RANGE;
    const endTop = STICKY_OFFSET;
    const root = document.documentElement;

    let raf = 0;
    function update() {
      raf = 0;
      const top = node!.getBoundingClientRect().top;
      const progress = Math.max(0, Math.min(1, (startTop - top) / (startTop - endTop)));
      root.style.setProperty("--bento-fade", String(progress));
      root.classList.toggle("bento-fade-half", progress > 0.5);
    }

    function onScroll() {
      if (raf) return;
      raf = requestAnimationFrame(update);
    }

    update();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    return () => {
      if (raf) cancelAnimationFrame(raf);
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
      // Reset on unmount so the variable doesn't leak into other pages.
      root.style.removeProperty("--bento-fade");
      root.classList.remove("bento-fade-half");
    };
  }, []);

  const [staged, setStaged] = useState<BentoIdentity>({
    title: entity.title ?? "",
    description: entity.description ?? "",
    icon: entity.icon ?? null,
    enabled: entity.enabled ?? 1,
  });

  // Reabsorb cache changes (e.g. from an immediate-save mutation) when not
  // mid-edit. Keys listed explicitly so the effect doesn't churn.
  useEffect(() => {
    setStaged({
      title: entity.title ?? "",
      description: entity.description ?? "",
      icon: entity.icon ?? null,
      enabled: entity.enabled ?? 1,
    });
  }, [entity.title, entity.description, entity.icon, entity.enabled]);

  async function persistField(patch: Partial<BentoIdentity>) {
    setStaged((prev) => ({ ...prev, ...patch }));
    if (isNew || !onUpdate) return; // staged-only — the form's Save creates it
    try {
      // Spreading a generic loses the exact TEntity shape for TS; the patch
      // keys are all BentoIdentity fields that exist on the entity, so the
      // merge is safe to assert back to TEntity.
      await onUpdate({ ...entity, ...patch } as TEntity);
      toast.success(t("forms.common.updated", { name: staged.title, feature }));
    } catch (err) {
      console.error("Failed to update entity field", err);
      toast.error(t("forms.common.notUpdated", { name: staged.title, feature }));
    }
  }

  async function handleDelete() {
    if (!onDelete) return;
    try {
      if (await onDelete()) {
        toast.success(t("forms.common.deleted", { name: staged.title, feature }));
        navigate(listRoute);
      } else {
        toast.error(t("forms.common.notDeleted", { name: staged.title, feature }));
      }
    } catch (err) {
      console.error("Failed to delete entity", err);
      toast.error(t("forms.common.notDeleted", { name: staged.title, feature }));
    }
    setDeleteOpen(false);
  }

  const enabled = staged.enabled === 1;
  const showSaveButtons = isNew || formState.isDirty;
  const titleMissing = !staged.title.trim();

  const actions: BentoActionsMenuItem[] = [
    ...(extraActions ?? []),
    ...(onDelete
      ? [{
          label: t("forms.formActions.delete"),
          icon: IconTrash,
          tone: "destructive" as const,
          onSelect: () => setDeleteOpen(true),
        }]
      : []),
  ];

  return (
    <>
      <BentoHero
        eyebrow={<Link to={listRoute} className="hover:text-foreground">{eyebrow}</Link>}
        leading={
          <BentoHeroIconPicker
            value={staged.icon}
            onChange={(nextIcon) => persistField({ icon: nextIcon })}
            onClear={() => persistField({ icon: null })}
            defaultIcon={icon}
            tone={tone}
            title={staged.title}
            description={staged.description}
          />
        }
        title={
          <BentoInlineEdit
            value={staged.title}
            onSave={(title) => persistField({ title })}
            placeholder={headlineFallback}
            className="text-3xl font-semibold tracking-tight md:text-4xl"
            ariaLabel={t("forms.common.title")}
            // Open in edit mode when creating so the user immediately sees a
            // focused input — a clear cue this is the first field to fill.
            autoFocus={isNew}
          />
        }
        subtitle={
          <BentoInlineEdit
            value={staged.description}
            onSave={(description) => persistField({ description })}
            multiline
            placeholder={t("forms.common.description")}
            className="max-w-2xl text-sm text-muted-foreground"
            ariaLabel={t("forms.common.description")}
          />
        }
        trailing={
          <>
            {showSaveButtons && (
              /*
               * Hero-anchored Save/Cancel. `bento-fade-out` reads the
               * inherited `--bento-fade` and applies opacity + translate;
               * pointer-events flip via `bento-fade-half` past the midpoint
               * so these stop catching clicks meant for the fixed save bar.
               * Bind to the form via the `form="…"` attribute.
               */
              <div className="bento-fade-out flex shrink-0 items-center gap-2">
                <GradientButton type="submit" form={formId} size="sm" loading={formState.isSubmitting} disabled={titleMissing}>
                  <IconDeviceFloppy className="size-4" />
                  {t("forms.formActions.saveChanges")}
                </GradientButton>
                <GradientButton type="button" size="sm" variant="outline" onClick={() => navigate(listRoute)}>
                  <IconX className="size-4" />
                  {t("forms.formActions.cancel")}
                </GradientButton>
              </div>
            )}
            {hasStatus && (
              <button
                type="button"
                onClick={() => persistField({ enabled: enabled ? 0 : 1 })}
                aria-label={t("forms.common.enabled")}
                title={enabled ? "Active — click to disable" : "Idle — click to enable"}
                className={`bento-tile-clickable flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[10px] uppercase tracking-wider transition-colors duration-200 ${
                  enabled
                    ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-600 hover:bg-emerald-500/20 dark:text-emerald-400"
                    : "border-border bg-muted text-muted-foreground hover:bg-muted/80"
                }`}
              >
                <span className={`h-1.5 w-1.5 rounded-full ${enabled ? "bg-emerald-500 bento-pulse" : "bg-muted-foreground/60"}`} />
                {enabled ? "Active" : "Idle"}
              </button>
            )}
            {!isNew && actions.length > 0 && <BentoActionsMenu actions={actions} />}
          </>
        }
      />

      {/* Sentinel for the scroll-driven progress — sits between hero and form. */}
      <div ref={heroSentinelRef} aria-hidden className="h-px" />

      {/*
       * Controlled DialogDelete — its built-in trigger is hidden so the
       * actions menu is the only entry point.
       */}
      {!isNew && onDelete && (
        <DialogDelete
          feature={feature}
          name={staged.title}
          onDelete={handleDelete}
          open={deleteOpen}
          setOpen={setDeleteOpen}
          trigger={<span className="hidden" aria-hidden />}
        />
      )}

      {children({ staged, onStateChange: setFormState })}
    </>
  );
}
