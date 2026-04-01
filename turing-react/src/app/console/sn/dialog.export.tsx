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
import { ContentExchangeProgressBar } from "@/components/ui/content-exchange-progress";
import { FloatingFormulasBg } from "@/components/ui/floating-formulas-bg";
import type { ContentExchangeProgress } from "@/services/sn/sn.service";
import { TurSNSiteService } from "@/services/sn/sn.service";
import { IconDownload } from "@tabler/icons-react";
import { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

interface Props {
  snSite: TurSNSite;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const turSNSiteService = new TurSNSiteService();

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
      <DialogContent className="sm:max-w-md !p-0">
        <div className="relative overflow-hidden rounded-lg p-6">
          <FloatingFormulasBg itemCount={8} />
          <div className="relative space-y-4">
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
                  <Button className="flex-1 h-11" variant="outline" onClick={() => handleClose(false)}>
                    {t("sn.export.cancel")}
                  </Button>
                  <GradientButton className="flex-1" onClick={handleExport}>
                    {t("sn.export.exportButton")}
                  </GradientButton>
                </DialogFooter>
              </>
            ) : (
              <div className="py-6">
                <ContentExchangeProgressBar
                  progress={progress}
                  label={t("sn.export.exportingContent")}
                  preparingLabel={t("sn.export.preparingExport")}
                />
              </div>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
