import { GradientButton } from "@/components/ui/gradient-button";
import { Button } from "@/components/ui/button";
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
import { TurFeaturesService } from "@/services/system/features.service";
import {
  IconDownload,
  IconDatabase,
  IconBrowserCheck,
  IconSettings2,
} from "@tabler/icons-react";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "@viglet/viglet-design-system";

interface Props {
  snSite: TurSNSite;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const turSNSiteService = new TurSNSiteService();
const turFeaturesService = new TurFeaturesService();

/* ── Toggle card for export options ── */
function OptionCard({
  icon,
  title,
  description,
  checked,
  onChange,
  disabled,
  hint,
}: Readonly<{
  icon: React.ReactNode;
  title: string;
  description: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  hint?: string;
}>) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => !disabled && onChange(!checked)}
      className={`flex items-start gap-3 w-full rounded-lg border p-3.5 text-left transition-all ${
        disabled
          ? "opacity-50 cursor-not-allowed border-border bg-muted/30"
          : checked
            ? "border-primary/50 bg-primary/5 ring-1 ring-primary/20"
            : "border-border hover:border-primary/30 hover:bg-accent/50 cursor-pointer"
      }`}
    >
      <div
        className={`mt-0.5 shrink-0 flex items-center justify-center size-8 rounded-md ${
          checked && !disabled
            ? "bg-primary/15 text-primary"
            : "bg-muted text-muted-foreground"
        }`}
      >
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium">{title}</span>
          {checked && !disabled && (
            <span className="inline-flex items-center rounded-full bg-primary/15 px-2 py-0.5 text-[10px] font-semibold text-primary">
              ON
            </span>
          )}
        </div>
        <p className="text-xs text-muted-foreground mt-0.5 leading-relaxed">
          {description}
        </p>
        {hint && (
          <p className="text-[11px] text-amber-600 dark:text-amber-400 mt-1">
            {hint}
          </p>
        )}
      </div>
    </button>
  );
}

/**
 * Per-site export dialog for the Bento Semantic Navigation instance page.
 *
 * Restores the export affordance the legacy console SN detail page carried
 * (retired in T570): config is always included; content and the search
 * template are opt-in toggles. Content export runs async with an SSE progress
 * bar; config-only export is a direct blob download.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function BentoSnExportDialog({ snSite, open, onOpenChange }: Readonly<Props>) {
  const { t } = useTranslation();
  const [includeContent, setIncludeContent] = useState(false);
  const [includeTemplate, setIncludeTemplate] = useState(false);
  const [storageEnabled, setStorageEnabled] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [progress, setProgress] = useState<ContentExchangeProgress | null>(null);

  const hasTemplate = !!snSite.searchTemplate && snSite.searchTemplate.trim() !== "";
  const canIncludeTemplate = hasTemplate && storageEnabled;

  useEffect(() => {
    if (open) {
      turFeaturesService.getFeatures()
        .then((f) => setStorageEnabled(f.storageEnabled))
        .catch(() => setStorageEnabled(false));
    }
  }, [open]);

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

    const shouldIncludeTemplate = includeTemplate && canIncludeTemplate;

    try {
      if (!includeContent) {
        const response = await turSNSiteService.export(snSite, shouldIncludeTemplate);
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
          shouldIncludeTemplate,
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
  }, [snSite, includeContent, includeTemplate, canIncludeTemplate, onOpenChange, t, downloadBlob]);

  const handleClose = useCallback(
    (value: boolean) => {
      if (!exporting) {
        onOpenChange(value);
        setIncludeContent(false);
        setIncludeTemplate(false);
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
          <div className="relative space-y-5">
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2">
                <IconDownload className="size-5" />
                {t("sn.export.title")}
              </DialogTitle>
              <DialogDescription>{t("sn.export.description")}</DialogDescription>
            </DialogHeader>

            {!exporting ? (
              <>
                {/* Always included */}
                <div className="flex items-center gap-2.5 rounded-lg bg-muted/50 border border-border/60 px-3 py-2.5">
                  <IconSettings2 className="size-4 text-primary shrink-0" />
                  <span className="text-sm text-foreground/80">{t("sn.export.configAlwaysIncluded")}</span>
                </div>

                {/* Option cards */}
                <div className="space-y-2.5">
                  <OptionCard
                    icon={<IconDatabase className="size-4" />}
                    title={t("sn.export.includeContent")}
                    description={t("sn.export.includeContentDescription")}
                    checked={includeContent}
                    onChange={setIncludeContent}
                  />

                  <OptionCard
                    icon={<IconBrowserCheck className="size-4" />}
                    title={t("sn.export.includeTemplate")}
                    description={
                      hasTemplate
                        ? t("sn.export.includeTemplateDescription", { name: snSite.searchTemplate })
                        : t("sn.export.noTemplateConfigured")
                    }
                    checked={includeTemplate}
                    onChange={setIncludeTemplate}
                    disabled={!canIncludeTemplate}
                    hint={
                      hasTemplate && !storageEnabled
                        ? t("sn.export.templateRequiresStorage")
                        : undefined
                    }
                  />
                </div>

                <DialogFooter>
                  <Button className="flex-1 h-11" variant="outline" onClick={() => handleClose(false)}>
                    {t("sn.export.cancel")}
                  </Button>
                  <GradientButton className="flex-1" onClick={handleExport}>
                    <IconDownload className="size-4" />
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
