import { LoadProvider } from "@/components/loading-provider";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { TurFeaturesService, type LoggingEngine } from "@/services/system/features.service";
import { IconDatabase, IconDatabaseOff } from "@tabler/icons-react";
import { useEffect, useState, type PropsWithChildren } from "react";
import { useTranslation } from "react-i18next";

const REFRESH_OPTIONS = [
  { label: "Off", value: 0 },
  { label: "1s", value: 1000 },
  { label: "5s", value: 5000 },
  { label: "10s", value: 10000 },
  { label: "30s", value: 30000 },
  { label: "1m", value: 60000 },
  { label: "5m", value: 300000 },
];

const turFeaturesService = new TurFeaturesService();

interface LoggingPageLayoutProps {
  pageData: unknown;
  error: string | null;
  tryAgainUrl: string;
  refreshInterval: number;
  onRefreshIntervalChange: (value: number) => void;
}

function EngineBadge({ engine }: { engine: LoggingEngine | undefined }) {
  const { t } = useTranslation();
  if (!engine) return null;
  const label = engine === "none" ? t("logging.engine.none") : engine.toUpperCase();
  const tone =
    engine === "mongodb"
      ? "bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-500/20"
      : engine === "redis"
        ? "bg-rose-500/10 text-rose-700 dark:text-rose-400 border-rose-500/20"
        : "bg-muted text-muted-foreground border-border";
  const Icon = engine === "none" ? IconDatabaseOff : IconDatabase;
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-md border px-2 py-0.5 text-xs font-medium ${tone}`}>
      <Icon className="size-3.5" />
      {label}
    </span>
  );
}

export const LoggingPageLayout: React.FC<PropsWithChildren<LoggingPageLayoutProps>> = ({
  pageData, error, tryAgainUrl, refreshInterval, onRefreshIntervalChange, children,
}) => {
  const { t } = useTranslation();
  const [engine, setEngine] = useState<LoggingEngine>();

  useEffect(() => {
    turFeaturesService.getFeatures()
      .then((f) => setEngine(f.loggingEngine))
      .catch(() => setEngine("none"));
  }, []);

  return (
    <LoadProvider checkIsNotUndefined={pageData} error={error} tryAgainUrl={tryAgainUrl}>
      <div className="flex items-center justify-between px-6 py-2 border-b">
        <EngineBadge engine={engine} />
        <div className="flex items-center gap-2">
          <span className="text-xs text-muted-foreground italic">Auto-refresh:</span>
          <Select
            value={String(refreshInterval)}
            onValueChange={(v) => onRefreshIntervalChange(Number(v))}
          >
            <SelectTrigger className="w-20 h-8 text-xs font-mono">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {REFRESH_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={String(opt.value)} className="text-xs">
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {engine === "none" ? (
        <div className="flex flex-col items-center justify-center text-center py-16 px-6 gap-2">
          <IconDatabaseOff className="size-10 text-muted-foreground/50" />
          <h2 className="text-base font-semibold">{t("logging.engine.notConfiguredTitle")}</h2>
          <p className="text-sm text-muted-foreground max-w-md">
            {t("logging.engine.notConfiguredDesc")}
          </p>
        </div>
      ) : (
        <div className="mt-4">
          {children}
        </div>
      )}
    </LoadProvider>
  );
};
