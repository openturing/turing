import { GradientButton } from "@/components/ui/gradient-button";
import { IconDeviceFloppy, IconX } from "@tabler/icons-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { BentoStatusMarker } from "./bento-status-marker";

export interface BentoSaveBarProps {
  /** Headline shown alongside the badges. Renders only when provided. */
  title?: string;
  badges?: ReactNode;
  /** Override the default Save / Cancel pair. */
  actions?: ReactNode;
  onCancel?: () => void;
  loading?: boolean;
  disabled?: boolean;
  /** Show the amber "unsaved changes" marker next to the title. */
  dirty?: boolean;
  /** Show the red "title required" marker (takes precedence over `dirty`). */
  titleMissing?: boolean;
}

export interface BentoSaveBarBadgeProps {
  children: ReactNode;
}

/**
 * Renderless save-bar surface (visual only). Frosted-glass + iOS
 * spring easing on the buttons.
 *
 * Position-agnostic: the consumer wraps it with whatever positioning
 * makes sense (sticky, fixed, scroll-linked transforms, etc). For
 * Bento detail pages it's typically wrapped in a `position: fixed`
 * container at `top-20` whose opacity/translate are driven by the
 * page's hero-scroll progress, so the bar feels like the header
 * morphing into a sticky bar as the user scrolls.
 */
function BentoSaveBar({
  title,
  badges,
  actions,
  onCancel,
  loading,
  disabled,
  dirty,
  titleMissing,
}: Readonly<BentoSaveBarProps>) {
  const { t } = useTranslation();

  return (
    <div className="bento-glass flex items-center justify-between gap-4 rounded-2xl px-4 py-3 shadow-md backdrop-blur-xl">
      <div className="flex min-w-0 items-center gap-3">
        {title && (
          <h3 className="truncate text-base font-semibold tracking-tight md:text-lg">
            {title}
          </h3>
        )}
        <BentoStatusMarker titleMissing={titleMissing} dirty={dirty} />
        {badges && <div className="flex shrink-0 items-center gap-1.5">{badges}</div>}
      </div>
      <div className="flex shrink-0 gap-2">
        {actions ?? (
          <>
            <GradientButton type="submit" size="sm" loading={loading} disabled={disabled}>
              <IconDeviceFloppy className="size-4" />
              {t("forms.formActions.saveChanges")}
            </GradientButton>
            {onCancel && (
              <GradientButton type="button" variant="outline" size="sm" onClick={onCancel}>
                <IconX className="size-4" />
                {t("forms.formActions.cancel")}
              </GradientButton>
            )}
          </>
        )}
      </div>
    </div>
  );
}

function BentoSaveBarBadge({ children }: Readonly<BentoSaveBarBadgeProps>) {
  return (
    <span className="rounded-full border border-border/60 bg-card/50 px-2 py-0.5 text-[10px] uppercase tracking-wider text-muted-foreground backdrop-blur">
      {children}
    </span>
  );
}

BentoSaveBar.Badge = BentoSaveBarBadge;

export { BentoSaveBar };
