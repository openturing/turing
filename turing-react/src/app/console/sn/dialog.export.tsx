import { GradientButton } from "@/components/ui/gradient-button";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import type { TurSNSite } from "@/models/sn/sn-site.model";
import {
  type ContentExchangeProgress,
  TurSNSiteService,
} from "@/services/sn/sn.service";
import { IconDownload, IconLoader2 } from "@tabler/icons-react";
import { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

interface Props {
  snSite: TurSNSite;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const turSNSiteService = new TurSNSiteService();

function formatTime(ms: number): string {
  if (ms < 0) return "--:--";
  const totalSec = Math.ceil(ms / 1000);
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${min}:${sec.toString().padStart(2, "0")}`;
}

export function DialogExport({ snSite, open, onOpenChange }: Props) {
  const { t } = useTranslation();
  const [includeContent, setIncludeContent] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [progress, setProgress] = useState<ContentExchangeProgress | null>(
    null,
  );
  const downloadBlob = useCallback(
    (blob: Blob) => {
      const fileName = `sn-site-${snSite.name}-${new Date().toISOString()}.zip`;
      const url = globalThis.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      globalThis.URL.revokeObjectURL(url);
      a.remove();
    },
    [snSite.name],
  );

  const handleExport = useCallback(async () => {
    setExporting(true);
    setProgress(null);

    try {
      if (!includeContent) {
        const response = await turSNSiteService.export(snSite);
        if (response) {
          downloadBlob(response);
          toast.success(t("sn.siteExported", { name: snSite.name }));
        } else {
          toast.error(t("sn.siteNotExported", { name: snSite.name }));
        }
      } else {
        const taskId = `export-${snSite.id}-${Date.now()}`;

        const asyncTaskId = await turSNSiteService.exportAsync(
          snSite.id.toString(),
          taskId,
        );
        if (!asyncTaskId) {
          toast.error(t("sn.siteNotExported", { name: snSite.name }));
          return;
        }

        await new Promise<void>((resolve) => {
          turSNSiteService.subscribeExportProgress(
            taskId,
            (data) => setProgress(data),
            () => resolve(),
            () => resolve(),
          );
        });

        const blob = await turSNSiteService.downloadExport(taskId);
        if (blob) {
          downloadBlob(blob);
          toast.success(t("sn.siteExported", { name: snSite.name }));
        } else {
          toast.error(t("sn.siteNotExported", { name: snSite.name }));
        }
      }
    } catch (error) {
      console.error("Export error", error);
      toast.error(t("sn.siteNotExported", { name: snSite.name }));
    } finally {
      setExporting(false);
      setProgress(null);
      onOpenChange(false);
    }
  }, [snSite, includeContent, onOpenChange, t, downloadBlob]);

  const handleClose = useCallback(
    (value: boolean) => {
      if (!exporting) {
        onOpenChange(value);
        setIncludeContent(false);
        setProgress(null);
      }
    },
    [exporting, onOpenChange],
  );

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <IconDownload className="size-5" />
            {t("sn.export.title")}
          </DialogTitle>
          <DialogDescription>{t("sn.export.description")}</DialogDescription>
        </DialogHeader>

        {!exporting ? (
          <>
            <div className="flex items-start gap-3 py-4">
              <Checkbox
                id="includeContent"
                checked={includeContent}
                onCheckedChange={(checked) =>
                  setIncludeContent(checked === true)
                }
              />
              <div className="grid gap-1.5 leading-none">
                <label
                  htmlFor="includeContent"
                  className="text-sm font-medium leading-none cursor-pointer"
                >
                  {t("sn.export.includeContent")}
                </label>
                <p className="text-xs text-muted-foreground">
                  {t("sn.export.includeContentDescription")}
                </p>
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => handleClose(false)}>
                {t("sn.export.cancel")}
              </Button>
              <GradientButton onClick={handleExport}>
                <IconDownload className="size-4 mr-2" />
                {t("sn.export.exportButton")}
              </GradientButton>
            </DialogFooter>
          </>
        ) : (
          <div className="py-6 space-y-4">
            <div className="flex items-center justify-center gap-2 text-blue-500">
              <IconLoader2 className="size-5 animate-spin" />
              <span className="text-sm font-medium">
                {progress
                  ? t("sn.export.exportingContent")
                  : t("sn.export.preparingExport")}
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
                <div className="flex justify-between text-xs text-muted-foreground">
                  <span>
                    {progress.processedDocuments.toLocaleString()} /{" "}
                    {progress.totalDocuments.toLocaleString()}{" "}
                    {t("sn.export.documents")}
                  </span>
                  <span>
                    {progress.percentage}%
                    {progress.estimatedRemainingMillis > 0 &&
                      ` - ${formatTime(progress.estimatedRemainingMillis)} ${t("sn.export.remaining")}`}
                  </span>
                </div>
                {progress.currentLocale && (
                  <p className="text-xs text-center text-muted-foreground">
                    {t("sn.export.locale")}: {progress.currentLocale}
                  </p>
                )}
              </div>
            )}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
