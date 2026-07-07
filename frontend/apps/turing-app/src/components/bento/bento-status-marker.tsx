import { IconAlertTriangle } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export interface BentoStatusMarkerProps {
  /**
   * The title is empty / missing. Takes precedence over `dirty` and renders
   * a red blocking marker — the same condition that disables the Save button.
   */
  titleMissing?: boolean;
  /** There are unsaved changes — renders an amber "unsaved" cue. */
  dirty?: boolean;
  className?: string;
}

/**
 * Small status chip surfacing the two save-state cues the Bento detail pages
 * carry (restored parity with the pre-redesign sticky header):
 *
 *   - `titleMissing` → a red "Title required" blocker (Save is also disabled).
 *   - `dirty`        → an amber "Unsaved changes" cue with a pulsing dot.
 *
 * Renders nothing when neither applies. Case/tracking are reset so it can sit
 * inside the hero eyebrow (which is uppercase + wide-tracked) without
 * inheriting those styles.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function BentoStatusMarker({ titleMissing, dirty, className }: Readonly<BentoStatusMarkerProps>) {
  const { t } = useTranslation();

  if (!titleMissing && !dirty) return null;

  const base =
    "inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-medium normal-case leading-none tracking-normal";

  if (titleMissing) {
    return (
      <span
        className={`${base} border-red-500/40 bg-red-500/10 text-red-600 dark:text-red-400 ${className ?? ""}`}
      >
        <IconAlertTriangle className="size-3" />
        {t("bento.saveBar.titleRequired")}
      </span>
    );
  }

  return (
    <span
      className={`${base} border-amber-500/40 bg-amber-500/10 text-amber-600 dark:text-amber-400 ${className ?? ""}`}
    >
      <span className="h-1.5 w-1.5 rounded-full bg-amber-500 bento-pulse" />
      {t("bento.saveBar.unsaved")}
    </span>
  );
}
