import { BadgeLocale } from "@/components/badge-locale";
import type { ContentExchangeProgress } from "@/services/sn/sn.service";
import { IconLoader2 } from "@tabler/icons-react";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

interface ContentExchangeProgressBarProps {
  progress: ContentExchangeProgress | null;
  label?: string;
  preparingLabel?: string;
}

const EMA_ALPHA = 0.15;
const MIN_UPDATE_INTERVAL_MS = 2000;

function formatTime(ms: number): string {
  if (ms <= 0) return "--:--";
  const totalSec = Math.ceil(ms / 1000);
  if (totalSec >= 3600) {
    const hrs = Math.floor(totalSec / 3600);
    const min = Math.floor((totalSec % 3600) / 60);
    return `${hrs}:${min.toString().padStart(2, "0")}:00`;
  }
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${min}:${sec.toString().padStart(2, "0")}`;
}

export function ContentExchangeProgressBar({
  progress,
  label,
  preparingLabel,
}: ContentExchangeProgressBarProps) {
  const { t } = useTranslation();

  const smoothedRef = useRef<number | null>(null);
  const lastDisplayUpdateRef = useRef(0);
  const [displayRemaining, setDisplayRemaining] = useState<number>(-1);

  useEffect(() => {
    if (!progress || progress.estimatedRemainingMillis <= 0) {
      smoothedRef.current = null;
      lastDisplayUpdateRef.current = 0;
      setDisplayRemaining(-1);
      return;
    }

    const raw = progress.estimatedRemainingMillis;

    if (smoothedRef.current === null) {
      smoothedRef.current = raw;
      lastDisplayUpdateRef.current = Date.now();
      setDisplayRemaining(raw);
      return;
    }

    smoothedRef.current =
      EMA_ALPHA * raw + (1 - EMA_ALPHA) * smoothedRef.current;

    const now = Date.now();
    if (now - lastDisplayUpdateRef.current >= MIN_UPDATE_INTERVAL_MS) {
      lastDisplayUpdateRef.current = now;
      setDisplayRemaining(smoothedRef.current);
    }
  }, [progress?.estimatedRemainingMillis, progress?.processedDocuments]);

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
