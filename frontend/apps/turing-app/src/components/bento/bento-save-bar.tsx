import { GradientButton } from "@/components/ui/gradient-button";
import { IconDeviceFloppy, IconX } from "@tabler/icons-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

export interface BentoSaveBarProps {
  title: string;
  badges?: ReactNode;
  /** Override the default Save / Cancel pair. */
  actions?: ReactNode;
  onCancel?: () => void;
  loading?: boolean;
  disabled?: boolean;
}

export interface BentoSaveBarBadgeProps {
  children: ReactNode;
}

/**
 * Sticky save bar styled to harmonize with the Bento UI.
 *
 * Frosted-glass surface (matches BentoUserMenu / tiles), generous
 * rounded-3xl corners, iOS spring easing on the buttons. Stays sticky
 * with `top-20` so it sits below the bento root header (which is
 * `sticky top-0`) — both layers stack cleanly while scrolling.
 */
function BentoSaveBar({
  title,
  badges,
  actions,
  onCancel,
  loading,
  disabled,
}: Readonly<BentoSaveBarProps>) {
  const { t } = useTranslation();

  return (
    <div className="bento-glass sticky top-20 z-20 flex items-center justify-between gap-4 rounded-2xl px-4 py-3 shadow-md backdrop-blur-xl">
      <div className="flex min-w-0 items-center gap-3">
        <h3 className="truncate text-base font-semibold tracking-tight md:text-lg">
          {title}
        </h3>
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
