import { BadgeLocale } from "@/components/badge-locale";
import { useSmoothedEta } from "@/hooks/use-smoothed-eta";
import { formatTime } from "@/lib/format-time";
import type { ContentExchangeProgress } from "@/services/sn/sn.service";
import { IconLoader2 } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

interface ContentExchangeProgressBarProps {
  progress: ContentExchangeProgress | null;
  label?: string;
  preparingLabel?: string;
}

export function ContentExchangeProgressBar({
  progress,
  label,
  preparingLabel,
}: ContentExchangeProgressBarProps) {
  const { t } = useTranslation();

  const displayRemaining = useSmoothedEta(progress?.estimatedRemainingMillis, {
    tick: progress?.processedDocuments,
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-center gap-2 text-blue-500">
        <IconLoader2 className="size-5 animate-spin" />
        <span className="text-sm font-medium">
          {progress
            ? (label ?? t("sn.export.exportingContent"))
            : (preparingLabel ?? t("sn.export.preparingExport"))}
        </span>
      </div>

      {progress && (
        <div className="space-y-2">
          <div className="w-full h-2 bg-muted rounded-full overflow-hidden">
            <div
              className="h-full bg-gradient-to-r from-blue-600 to-indigo-600 rounded-full transition-all duration-300 ease-out"
              style={{ width: `${progress.percentage}%` }}
            />
          </div>
          <div className="flex flex-col items-center gap-1 text-xs text-muted-foreground">
            <span>
              {progress.processedDocuments.toLocaleString()} /{" "}
              {progress.totalDocuments.toLocaleString()}{" "}
              {t("sn.export.documents")} — {progress.percentage}%
            </span>
            {displayRemaining > 0 && (
              <span>
                {formatTime(displayRemaining)} {t("sn.export.remaining")}
              </span>
            )}
          </div>
          {progress.currentLocale && (
            <div className="flex justify-center">
              <BadgeLocale locale={progress.currentLocale} />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
