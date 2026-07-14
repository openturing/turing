import { GradientButton } from "@/components/ui/gradient-button";
import { IconDeviceFloppy, IconX } from "@tabler/icons-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { BentoBackLink, BentoHero, type BentoHeroProps } from "./bento-hero";
import { BentoScrollSaveBar } from "./bento-scroll-save-bar";

export interface BentoFormHeroProps extends BentoHeroProps {
  /**
   * Convenience back-link breadcrumb: when set, the hero renders its own
   * `eyebrow` as the shared {@link BentoBackLink} (arrow + label) — the same
   * affordance `BentoEntityShell` and `BentoHero` bake in — so every form page
   * gets a consistent "← back" breadcrumb without hand-assembling it. Overrides
   * `eyebrow`. Pair with {@link backLabel}.
   */
  backTo?: string;
  /** Text of the {@link backTo} breadcrumb (e.g. the parent entity name). */
  backLabel?: ReactNode;
  /** Cancel / back navigation (both the hero button and the sticky bar). */
  onCancel: () => void;
  /** Submit in progress — spins the Save button in both places. */
  loading?: boolean;
  /** Amber "unsaved changes" marker + enables Save. */
  dirty?: boolean;
  /** Red "title required" marker; disables Save (takes precedence over dirty). */
  titleMissing?: boolean;
  /**
   * Force-disable Save. When omitted, Save is disabled while `titleMissing` or
   * while there is nothing to save (`!dirty`).
   */
  saveDisabled?: boolean;
  /** Title shown in the fixed sticky bar once the hero scrolls away. */
  stickyTitle?: string;
  /** Badges shown next to the sticky-bar title. */
  stickyBadges?: ReactNode;
}

/**
 * The **single drop-in** that gives a Bento form page the exact "hero → sticky
 * save bar" morph the {@link BentoEntityShell} pioneered (the LLM/AI-agent
 * detail look): at the top, Save/Cancel live in the hero and fade + lift away
 * as you scroll; once the hero is gone a fixed frosted save bar fades in below
 * the shell header.
 *
 * Use it for form pages that render their **own** hero and therefore can't use
 * {@link BentoEntityShell} — e.g. the persona editor (a shared form behind a
 * pill tab-bar) or any surface with a custom title/leading. It wraps
 * {@link BentoHero} (injecting the fade-out Save/Cancel into `trailing`,
 * *after* any extra `trailing` you pass such as a status pill or a `⋮`
 * {@link BentoActionsMenu}) and renders the {@link BentoScrollSaveBar} twin — so
 * the two halves can never drift apart or be half-wired.
 *
 * Render it as the first child **inside the `<form>`** it submits — both the
 * hero Save button and the sticky-bar Save button are plain `type="submit"`
 * controls that post the enclosing form, so the hero must live in the form.
 *
 * ```tsx
 * <form onSubmit={handleSubmit(onSubmit)}>
 *   <BentoFormHero
 *     eyebrow={<Link to={listRoute}>{feature}</Link>}
 *     leading={<IconChip />}
 *     title={watchedTitle}
 *     subtitle={description}
 *     trailing={!isNew && <BentoActionsMenu actions={[del]} />}
 *     onCancel={() => navigate(listRoute)}
 *     loading={isSubmitting}
 *     titleMissing={!watchedTitle.trim()}
 *     dirty={isNew || isDirty}
 *     stickyTitle={watchedTitle}
 *   />
 *   {/* pill nav + sections … *​/}
 * </form>
 * ```
 *
 * Keep destructive actions in the hero `trailing` (`⋮` menu) — never in the
 * save bar, which is Save/Cancel only.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function BentoFormHero({
  eyebrow,
  backTo,
  backLabel,
  leading,
  title,
  subtitle,
  trailing,
  onCancel,
  loading,
  dirty,
  titleMissing,
  saveDisabled,
  stickyTitle,
  stickyBadges,
}: Readonly<BentoFormHeroProps>) {
  const { t } = useTranslation();
  const disabled = saveDisabled ?? (Boolean(titleMissing) || !dirty);

  // A `backTo` route wins over a hand-passed `eyebrow` and renders the standard
  // "← label" breadcrumb via the shared {@link BentoBackLink} (matches
  // BentoEntityShell's and BentoHero's built-in back-arrow).
  const resolvedEyebrow = backTo !== undefined ? (
    <BentoBackLink to={backTo}>{backLabel}</BentoBackLink>
  ) : eyebrow;

  return (
    <>
      <BentoHero
        eyebrow={resolvedEyebrow}
        leading={leading}
        title={title}
        subtitle={subtitle}
        trailing={
          <>
            {/*
             * Hero-anchored Save/Cancel. `bento-fade-out` reads the inherited
             * `--bento-fade` and applies opacity + translate; pointer-events
             * flip via `bento-fade-half` past the midpoint so these stop
             * catching clicks meant for the fixed bar.
             */}
            <div className="bento-fade-out flex shrink-0 items-center gap-2">
              <GradientButton type="submit" size="sm" loading={loading} disabled={disabled}>
                <IconDeviceFloppy className="size-4" />
                {t("forms.formActions.saveChanges")}
              </GradientButton>
              <GradientButton type="button" variant="outline" size="sm" onClick={onCancel}>
                <IconX className="size-4" />
                {t("forms.formActions.cancel")}
              </GradientButton>
            </div>
            {trailing}
          </>
        }
      />
      <BentoScrollSaveBar
        title={stickyTitle}
        badges={stickyBadges}
        onCancel={onCancel}
        loading={loading}
        disabled={disabled}
        dirty={dirty}
        titleMissing={titleMissing}
      />
    </>
  );
}
