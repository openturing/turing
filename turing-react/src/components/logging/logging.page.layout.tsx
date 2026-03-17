import { LoadProvider } from "@/components/loading-provider";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { PropsWithChildren } from "react";

const REFRESH_OPTIONS = [
  { label: "Off", value: 0 },
  { label: "1s", value: 1000 },
  { label: "5s", value: 5000 },
  { label: "10s", value: 10000 },
  { label: "30s", value: 30000 },
  { label: "1m", value: 60000 },
  { label: "5m", value: 300000 },
];

interface LoggingPageLayoutProps {
  pageData: unknown;
  error: string | null;
  tryAgainUrl: string;
  refreshInterval: number;
  onRefreshIntervalChange: (value: number) => void;
}

export const LoggingPageLayout: React.FC<PropsWithChildren<LoggingPageLayoutProps>> = ({
  pageData, error, tryAgainUrl, refreshInterval, onRefreshIntervalChange, children,
}) => (
  <LoadProvider checkIsNotUndefined={pageData} error={error} tryAgainUrl={tryAgainUrl}>
    <div className="flex items-center justify-end px-6 py-2 border-b">
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
    <div className="mt-4">
      {children}
    </div>
  </LoadProvider>
);
