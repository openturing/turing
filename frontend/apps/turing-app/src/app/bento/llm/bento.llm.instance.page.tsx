import { useDeleteLlmInstance, useLlmInstance, useUpdateLlmInstance } from "@/api/queries/llm-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoActionsMenu, BentoHero, BentoHeroIconPicker, BentoInlineEdit } from "@/components/bento";
import { DialogDelete } from "@/components/dialog.delete";
import { LoadProvider } from "@/components/loading-provider";
import { GradientButton } from "@/components/ui/gradient-button";
import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts";
import { IconCpu2, IconDeviceFloppy, IconTrash, IconX } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router-dom";
import { BENTO_LLM_FORM_ID, BentoLLMInstanceForm } from "./bento.llm.instance.form";

export default function BentoLLMInstancePage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const isNew = id === "new";

  const { data: llm, isError } = useLlmInstance(isNew ? undefined : id);
  const error = isError ? t("common.connectionError", { resource: t("llm.title") }) : null;

  if (isNew) {
    return (
      <Shell entity={{} as TurLLMInstance} isNew headlineFallback={t("llm.newLanguageModel")}>
        {({ staged, onStateChange }) => (
          <BentoLLMInstanceForm
            value={{} as TurLLMInstance}
            isNew
            staged={staged}
            onStateChange={onStateChange}
          />
        )}
      </Shell>
    );
  }

  return (
    <LoadProvider checkIsNotUndefined={llm} error={error} tryAgainUrl={`${ROUTES.BENTO_LLM_INSTANCE}/${id}`}>
      {llm && (
        <Shell entity={llm} isNew={false} headlineFallback={llm.title}>
          {({ staged, onStateChange }) => (
            <BentoLLMInstanceForm
              value={llm}
              isNew={false}
              staged={staged}
              onStateChange={onStateChange}
            />
          )}
        </Shell>
      )}
    </LoadProvider>
  );
}

/**
 * State held by the hero — title, description, status, icon. Form
 * receives this so its submit payload always carries the latest hero
 * values, regardless of whether they were already persisted (existing
 * mode, immediate-save) or are still staging (new mode, save with
 * the rest on form Save).
 */
export interface BentoLLMHeroStaged {
  title: string;
  description: string;
  enabled: number;
  icon: string | null;
}

interface ShellRenderArgs {
  staged: BentoLLMHeroStaged;
  /** Form state callback — Shell uses isDirty/isSubmitting to render hero-anchored Save/Cancel buttons. */
  onStateChange: (state: { isDirty: boolean; isSubmitting: boolean }) => void;
}

interface ShellProps {
  entity: Pick<TurLLMInstance, "id" | "title" | "description" | "enabled" | "icon"> & Partial<TurLLMInstance>;
  isNew: boolean;
  headlineFallback: string;
  children: (args: ShellRenderArgs) => React.ReactNode;
}

/*
 * Hero owns the entity's "identity" fields — title, description,
 * status, icon. Two modes:
 *
 *   - Existing instance: each field commits immediately via the
 *     update mutation (iOS-style: type, blur, it's saved). The form's
 *     reset uses `keepDirtyValues: true` so unsaved edits to URL /
 *     model / API key aren't clobbered by the cache update.
 *
 *   - New instance: fields stage in local state. The form receives
 *     them via `staged` and merges them into the create payload on
 *     Save. No mutation fires until the user explicitly saves.
 *
 * The form removes title / description / enabled FormFields in both
 * modes — the hero is the canonical place to edit these.
 */
function Shell({ entity, isNew, headlineFallback, children }: Readonly<ShellProps>) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const updateMutation = useUpdateLlmInstance();
  const deleteMutation = useDeleteLlmInstance();
  const [deleteOpen, setDeleteOpen] = useState(false);

  /*
   * Scroll-linked "hero-to-sticky-bar morph" progress. We compute a
   * 0..1 value from the sentinel's viewport position on every scroll
   * frame and write it as a CSS custom property `--bento-fade` on
   * `<html>`. CSS inheritance distributes it everywhere, so the
   * hero buttons (`bento-fade-out`), the fixed save bar
   * (`bento-fade-in`), and the layout spacer (`bento-save-bar-spacer`)
   * all interpolate without any React state — zero re-renders during
   * scroll, all visual updates happen in the compositor.
   *
   * Range:
   *   - sentinel at 160px (STICKY_OFFSET + REVEAL_RANGE) → progress 0
   *   - sentinel at  80px (STICKY_OFFSET)               → progress 1
   *
   * `bento-fade-half` is also toggled on `<html>` past 0.5 — used by
   * the CSS to flip pointer-events between the two element groups
   * once the visual midpoint is crossed.
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
      const t = Math.max(0, Math.min(1, (startTop - top) / (startTop - endTop)));
      root.style.setProperty("--bento-fade", String(t));
      root.classList.toggle("bento-fade-half", t > 0.5);
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

  const [formState, setFormState] = useState({ isDirty: false, isSubmitting: false });

  const [staged, setStaged] = useState<BentoLLMHeroStaged>({
    title: entity.title ?? "",
    description: entity.description ?? "",
    enabled: entity.enabled ?? 1,
    icon: entity.icon ?? null,
  });

  // Reabsorb cache changes (e.g. from immediate-save mutation) when not
  // mid-edit. Keys explicitly listed so the effect doesn't churn on
  // unrelated entity changes.
  useEffect(() => {
    setStaged({
      title: entity.title ?? "",
      description: entity.description ?? "",
      enabled: entity.enabled ?? 1,
      icon: entity.icon ?? null,
    });
  }, [entity.title, entity.description, entity.enabled, entity.icon]);

  async function persistField(patch: Partial<BentoLLMHeroStaged>) {
    setStaged((prev) => ({ ...prev, ...patch }));
    if (isNew) return; // staged-only — form Save creates the entity
    try {
      await updateMutation.mutateAsync({ ...(entity as TurLLMInstance), ...patch });
      toast.success(t("forms.common.updated", { name: entity.title, feature: t("llm.title") }));
    } catch (err) {
      console.error("Failed to update LLM field", err);
      toast.error(t("forms.common.notUpdated", { name: entity.title, feature: t("llm.title") }));
    }
  }

  /*
   * Delete lives in the hero's `⋮` dropdown — same pattern as the AI
   * Agent dashboard, kept consistent across all bento detail pages.
   * The save bar in the form no longer hosts destructive actions.
   */
  async function onDelete() {
    try {
      if (await deleteMutation.mutateAsync(entity as TurLLMInstance)) {
        toast.success(t("forms.common.deleted", { name: entity.title, feature: t("llm.title") }));
        navigate(ROUTES.BENTO_LLM_INSTANCE);
      } else {
        toast.error(t("forms.common.notDeleted", { name: entity.title, feature: t("llm.title") }));
      }
    } catch (err) {
      console.error("Failed to delete LLM instance", err);
      toast.error(t("forms.common.notDeleted", { name: entity.title, feature: t("llm.title") }));
    }
    setDeleteOpen(false);
  }

  const enabled = staged.enabled === 1;
  /*
   * Hero-anchored Save/Cancel show whenever there's something to save
   * (form is dirty) or we're creating a new instance. Title-required
   * gating mirrors the form's own check — the same gate that disables
   * the sticky save bar's Save button.
   */
  const showSaveButtons = isNew || formState.isDirty;
  const titleMissing = !staged.title.trim();

  return (
    <>
      <BentoHero
        eyebrow={
          <Link to={ROUTES.BENTO_LLM_INSTANCE} className="hover:text-foreground">
            {t("llm.title")}
          </Link>
        }
        leading={
          <BentoHeroIconPicker
            value={staged.icon}
            onChange={(icon) => persistField({ icon })}
            onClear={() => persistField({ icon: null })}
            defaultIcon={IconCpu2}
            tone="indigo"
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
            // Open the title in edit mode when creating a new instance
            // so the user immediately sees a focused input — clear cue
            // that this is the first field to fill in.
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
               * inherited `--bento-fade` from <html> (set by the
               * scroll-linked rAF loop above) and applies the
               * opacity + translate. Pointer-events also flip via CSS
               * once `bento-fade-half` toggles on <html> past the
               * midpoint, so these buttons stop catching clicks
               * meant for the fade-in save bar below.
               *
               * Buttons bind to the form via the `form="..."` HTML5
               * attribute — submission works from outside the form
               * element.
               */
              <div className="bento-fade-out flex shrink-0 items-center gap-2">
                <GradientButton
                  type="submit"
                  form={BENTO_LLM_FORM_ID}
                  size="sm"
                  loading={formState.isSubmitting}
                  disabled={titleMissing}
                >
                  <IconDeviceFloppy className="size-4" />
                  {t("forms.formActions.saveChanges")}
                </GradientButton>
                <GradientButton
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => navigate(ROUTES.BENTO_LLM_INSTANCE)}
                >
                  <IconX className="size-4" />
                  {t("forms.formActions.cancel")}
                </GradientButton>
              </div>
            )}
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
            {!isNew && (
              <BentoActionsMenu
                actions={[
                  {
                    label: t("forms.formActions.delete"),
                    icon: IconTrash,
                    tone: "destructive",
                    onSelect: () => setDeleteOpen(true),
                  },
                ]}
              />
            )}
          </>
        }
      />

      {/* Sentinel for the scroll-driven progress. Sits between the
          hero and the form content; the rAF loop reads its top each
          frame to compute how far along the hero→sticky-bar morph
          should be. */}
      <div ref={heroSentinelRef} aria-hidden className="h-px" />

      {/*
       * Controlled DialogDelete — its built-in trigger is hidden so the
       * actions menu is the only entry point. Mirrors the AI Agent
       * dashboard pattern.
       */}
      {!isNew && (
        <DialogDelete
          feature={t("llm.title")}
          name={entity.title ?? ""}
          onDelete={onDelete}
          open={deleteOpen}
          setOpen={setDeleteOpen}
          trigger={<span className="hidden" aria-hidden />}
        />
      )}

      {children({ staged, onStateChange: setFormState })}
    </>
  );
}
