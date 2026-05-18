import { GradientButton } from "@/components/ui/gradient-button";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ImportResultPanel } from "@/components/ui/import-result-panel";
import { ImportOptionsPanel } from "@/components/ui/import-options-panel";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { ContentExchangeProgressBar } from "@/components/ui/content-exchange-progress";
import { FloatingFormulasBg } from "@/components/ui/floating-formulas-bg";
import type { TurImportResult } from "@/models/marketplace/import-result.model";
import type { ContentExchangeProgress } from "@/services/sn/sn.service";
import { TurExchangeImportService, type ZipCheckResult } from "@/services/exchange/exchange.import.service";
import { TurFeaturesService } from "@/services/system/features.service";
import {
    IconCheck,
    IconCloudUpload,
    IconFileZip,
    IconLoader2,
    IconPackageImport,
    IconX,
} from "@tabler/icons-react";
import { useCallback, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "@viglet/viglet-design-system";

const importService = new TurExchangeImportService();
const featuresService = new TurFeaturesService();

type ImportStatus = "idle" | "uploading" | "success" | "error" | "import-prompt" | "indexing";

export default function ExchangeImportPage() {
    const [isDragOver, setIsDragOver] = useState(false);
    const [file, setFile] = useState<File | null>(null);
    const [status, setStatus] = useState<ImportStatus>("idle");
    const [progress, setProgress] = useState(0);
    const [contentProgress, setContentProgress] = useState<ContentExchangeProgress | null>(null);
    const [errorMessage, setErrorMessage] = useState<string>("");
    const [zipCheck, setZipCheck] = useState<ZipCheckResult | null>(null);
    const [storageEnabled, setStorageEnabled] = useState(false);
    const [includeContent, setIncludeContent] = useState(false);
    const [includeTemplate, setIncludeTemplate] = useState(false);
    const [overwrite, setOverwrite] = useState(false);
    const [importResult, setImportResult] = useState<TurImportResult | null>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const { t } = useTranslation();

    const isValidFile = (f: File): boolean => {
        const validTypes = [
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream",
        ];
        return validTypes.includes(f.type) || f.name.endsWith(".zip");
    };

    const doImport = useCallback(async (selectedFile: File, withContent: boolean, withTemplate: boolean, overwrite = false) => {
        setStatus("uploading");
        setProgress(0);
        setErrorMessage("");
        setImportResult(null);

        const taskId = `import-${Date.now()}`;

        try {
            if (withContent) {
                setStatus("indexing");
                importService.subscribeImportProgress(
                    taskId,
                    (data) => setContentProgress(data),
                    () => { },
                    () => { },
                );
            }
            const result = await importService.importFile(
                selectedFile,
                (p) => setProgress(p),
                withContent,
                withTemplate,
                withContent ? taskId : undefined,
                overwrite,
            );
            setImportResult(result);
            setContentProgress(null);
            if (result.error) {
                setErrorMessage(result.error);
                setStatus("error");
                toast.error(t("exchange.importFailedMsg", { message: result.error }));
            } else {
                setStatus("success");
                setProgress(100);
                toast.success(t("exchange.importSuccess"));
            }
        } catch (err: any) {
            const msg =
                err?.response?.data?.message ||
                err?.message ||
                t("exchange.importError");
            setErrorMessage(msg);
            setStatus("error");
            setContentProgress(null);
            toast.error(t("exchange.importFailedMsg", { message: msg }));
        }
    }, [t]);

    const handleFile = useCallback(
        async (selectedFile: File) => {
            if (!isValidFile(selectedFile)) {
                setErrorMessage(t("exchange.invalidFile"));
                setStatus("error");
                toast.error(t("exchange.invalidType"));
                return;
            }

            setFile(selectedFile);
            setStatus("uploading");
            setProgress(0);
            setErrorMessage("");
            setIncludeContent(false);
            setIncludeTemplate(false);
            setOverwrite(false);

            try {
                const [check, features] = await Promise.all([
                    importService.checkZip(selectedFile),
                    featuresService.getFeatures().catch(() => ({ storageEnabled: false, ragEnabled: false })),
                ]);
                setZipCheck(check);
                setStorageEnabled(features.storageEnabled);

                const hasConflicts = (check.conflicts?.length ?? 0) > 0;
                const hasAgentConflicts = (check.agentConflicts?.length ?? 0) > 0;
                if (check.hasContent || check.hasTemplate || hasConflicts || hasAgentConflicts) {
                    setStatus("import-prompt");
                    return;
                }
                // Nothing extra detected — import directly
                await doImport(selectedFile, false, false);
            } catch (err: any) {
                const msg =
                    err?.response?.data?.message ||
                    err?.message ||
                    t("exchange.importError");
                setErrorMessage(msg);
                setStatus("error");
                toast.error(t("exchange.importFailedMsg", { message: msg }));
            }
        },
        [doImport, t]
    );

    const handleDragOver = useCallback(
        (e: React.DragEvent<HTMLDivElement>) => {
            e.preventDefault();
            e.stopPropagation();
            if (status !== "uploading") {
                setIsDragOver(true);
            }
        },
        [status]
    );

    const handleDragLeave = useCallback(
        (e: React.DragEvent<HTMLDivElement>) => {
            e.preventDefault();
            e.stopPropagation();
            setIsDragOver(false);
        },
        []
    );

    const handleDrop = useCallback(
        (e: React.DragEvent<HTMLDivElement>) => {
            e.preventDefault();
            e.stopPropagation();
            setIsDragOver(false);

            if (status === "uploading") return;

            const droppedFile = e.dataTransfer.files?.[0];
            if (droppedFile) {
                handleFile(droppedFile);
            }
        },
        [status, handleFile]
    );

    const handleFileChange = useCallback(
        (e: React.ChangeEvent<HTMLInputElement>) => {
            const selectedFile = e.target.files?.[0];
            if (selectedFile) {
                handleFile(selectedFile);
            }
        },
        [handleFile]
    );

    const handleClick = useCallback(() => {
        if (status !== "uploading") {
            fileInputRef.current?.click();
        }
    }, [status]);

    const handleReset = useCallback(() => {
        setFile(null);
        setStatus("idle");
        setProgress(0);
        setErrorMessage("");
        setZipCheck(null);
        setIncludeContent(false);
        setIncludeTemplate(false);
        setOverwrite(false);
        setImportResult(null);
        if (fileInputRef.current) {
            fileInputRef.current.value = "";
        }
    }, []);

    const formatFileSize = (bytes: number): string => {
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`;
        return `${(bytes / 1048576).toFixed(1)} MB`;
    };

    const canIncludeTemplate = zipCheck?.hasTemplate === true && storageEnabled;

    return (
        <div className="flex flex-col items-center px-4 md:px-6 lg:px-8 py-2">
            <div className="w-full max-w-2xl space-y-6">
                {/* Drop zone */}
                <Card
                    className={`
            relative overflow-hidden transition-all duration-300 ease-out
            ${status === "uploading" ? "cursor-wait" : "cursor-pointer"}
            ${isDragOver
                            ? "border-blue-500 bg-blue-500/5 shadow-lg shadow-blue-500/10 scale-[1.01]"
                            : status === "success"
                                ? "border-emerald-500/50 bg-emerald-500/5"
                                : status === "error"
                                    ? "border-red-500/50 bg-red-500/5"
                                    : "border-dashed border-muted-foreground/25 hover:border-muted-foreground/50 hover:bg-accent/50"
                        }
          `}
                    onClick={handleClick}
                    onDragOver={handleDragOver}
                    onDragLeave={handleDragLeave}
                    onDrop={handleDrop}
                >
                    <FloatingFormulasBg />

                    {status === "uploading" && (
                        <div
                            className="absolute inset-0 bg-blue-500/8 transition-all duration-300 ease-out"
                            style={{ width: `${progress}%` }}
                        />
                    )}

                    <CardContent className="relative flex flex-col items-center justify-center py-16 gap-4">
                        <input
                            ref={fileInputRef}
                            type="file"
                            accept=".zip,application/zip,application/x-zip-compressed"
                            onChange={handleFileChange}
                            className="hidden"
                            id="import-file-input"
                            aria-label={t("exchange.selectZip")}
                        />

                        <div
                            className={`
                rounded-2xl p-4 transition-all duration-300
                ${isDragOver
                                    ? "bg-blue-500/15 text-blue-500 scale-110"
                                    : status === "uploading"
                                        ? "bg-blue-500/10 text-blue-500"
                                        : status === "success"
                                            ? "bg-emerald-500/10 text-emerald-500"
                                            : status === "error"
                                                ? "bg-red-500/10 text-red-500"
                                                : "bg-muted text-muted-foreground"
                                }
              `}
                        >
                            {status === "uploading" ? (
                                <IconLoader2 className="size-10 animate-spin" />
                            ) : status === "success" ? (
                                <IconCheck className="size-10" />
                            ) : status === "error" ? (
                                <IconX className="size-10" />
                            ) : (
                                <IconCloudUpload className="size-10" />
                            )}
                        </div>

                        <div className="text-center space-y-2">
                            {status === "idle" && (
                                <>
                                    <p className="text-lg font-semibold">
                                        {isDragOver
                                            ? t("exchange.dropFile")
                                            : t("exchange.dragDrop")}
                                    </p>
                                    <p className="text-sm text-muted-foreground">
                                        or{" "}
                                        <span className="text-blue-500 font-medium underline underline-offset-4 decoration-blue-500/30 hover:decoration-blue-500">
                                            {t("exchange.browseFiles")}
                                        </span>{" "}
                                        {t("exchange.selectZip")}
                                    </p>
                                    <p className="text-xs text-muted-foreground/60 pt-1">
                                        {t("exchange.onlyZip")}
                                    </p>
                                </>
                            )}

                            {status === "uploading" && (
                                <>
                                    <p className="text-lg font-semibold text-blue-500">
                                        {t("exchange.importing")}
                                    </p>
                                    <p className="text-sm text-muted-foreground">
                                        {progress}% {t("exchange.uploaded")}
                                    </p>
                                </>
                            )}

                            {status === "success" && (
                                <div className="w-full max-w-sm">
                                    {importResult ? (
                                        <ImportResultPanel result={importResult} />
                                    ) : (
                                        <p className="text-lg font-semibold text-emerald-500">
                                            {t("exchange.importCompleted")}
                                        </p>
                                    )}
                                </div>
                            )}

                            {status === "indexing" && (
                                <div className="w-full max-w-xs mt-2">
                                    <ContentExchangeProgressBar
                                        progress={contentProgress}
                                        label={t("exchange.indexingContent")}
                                        preparingLabel={t("exchange.indexingContent")}
                                    />
                                </div>
                            )}

                            {status === "error" && (
                                <>
                                    <p className="text-lg font-semibold text-red-500">
                                        {t("exchange.importFailed")}
                                    </p>
                                    <p className="text-sm text-muted-foreground">
                                        {errorMessage}
                                    </p>
                                    <button
                                        type="button"
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            handleReset();
                                        }}
                                        className="mt-2 text-sm text-blue-500 font-medium underline underline-offset-4 decoration-blue-500/30 hover:decoration-blue-500 cursor-pointer"
                                    >
                                        Try again
                                    </button>
                                </>
                            )}
                        </div>
                    </CardContent>
                </Card>

                {/* File info */}
                {file && status !== "idle" && status !== "import-prompt" && (
                    <Card className="transition-all duration-300 animate-in fade-in-0 slide-in-from-bottom-2">
                        <CardContent className="flex items-center gap-4 py-4">
                            <div
                                className={`
                  rounded-xl p-2.5 shrink-0
                  ${status === "success"
                                        ? "bg-emerald-500/10 text-emerald-500"
                                        : status === "error"
                                            ? "bg-red-500/10 text-red-500"
                                            : "bg-blue-500/10 text-blue-500"
                                    }
                `}
                            >
                                <IconFileZip className="size-5" />
                            </div>
                            <div className="flex-1 min-w-0">
                                <p className="text-sm font-medium truncate">{file.name}</p>
                                <p className="text-xs text-muted-foreground">
                                    {formatFileSize(file.size)}
                                </p>
                            </div>
                            {status === "uploading" && (
                                <div className="shrink-0">
                                    <div className="w-24 h-1.5 bg-muted rounded-full overflow-hidden">
                                        <div
                                            className="h-full bg-blue-500 rounded-full transition-all duration-300 ease-out"
                                            style={{ width: `${progress}%` }}
                                        />
                                    </div>
                                </div>
                            )}
                            {status === "success" && (
                                <IconCheck className="size-5 text-emerald-500 shrink-0" />
                            )}
                            {status === "error" && (
                                <button
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        handleReset();
                                    }}
                                    title="Dismiss"
                                    className="shrink-0 rounded-lg p-1.5 hover:bg-muted transition-colors cursor-pointer"
                                >
                                    <IconX className="size-4 text-muted-foreground" />
                                </button>
                            )}
                        </CardContent>
                    </Card>
                )}
            </div>

            {/* Import options dialog */}
            <Dialog open={status === "import-prompt"} onOpenChange={(open) => {
                if (!open) handleReset();
            }}>
                <DialogContent className="sm:max-w-md !p-0">
                    <div className="relative overflow-hidden rounded-lg p-6">
                        <FloatingFormulasBg itemCount={8} />
                        <div className="relative space-y-5">
                            <DialogHeader>
                                <DialogTitle className="flex items-center gap-2">
                                    <IconPackageImport className="size-5" />
                                    {t("exchange.importOptions.title")}
                                </DialogTitle>
                                <DialogDescription>{t("exchange.importOptions.description")}</DialogDescription>
                            </DialogHeader>

                            {/* File info mini */}
                            {file && (
                                <div className="flex items-center gap-3 rounded-lg bg-muted/50 border border-border/60 px-3 py-2.5">
                                    <IconFileZip className="size-4 text-muted-foreground shrink-0" />
                                    <span className="text-sm truncate">{file.name}</span>
                                    <span className="text-xs text-muted-foreground ml-auto shrink-0">{formatFileSize(file.size)}</span>
                                </div>
                            )}

                            <ImportOptionsPanel
                                conflicts={zipCheck?.conflicts ?? []}
                                agentConflicts={zipCheck?.agentConflicts ?? []}
                                hasContent={zipCheck?.hasContent ?? false}
                                hasTemplate={zipCheck?.hasTemplate ?? false}
                                storageEnabled={storageEnabled}
                                values={{ includeContent, includeTemplate, overwrite }}
                                onChange={{
                                    setIncludeContent,
                                    setIncludeTemplate,
                                    setOverwrite,
                                }}
                                labels={{
                                    configAlwaysIncluded: t("exchange.importOptions.configAlwaysIncluded"),
                                    conflictWarningTitle: t("exchange.importOptions.conflictWarningTitle", { count: (zipCheck?.conflicts?.length ?? 0) + (zipCheck?.agentConflicts?.length ?? 0) }),
                                    overwriteConfig: t("exchange.importOptions.overwriteConfig"),
                                    overwriteConfigOn: t("exchange.importOptions.overwriteConfigOn"),
                                    overwriteConfigOff: t("exchange.importOptions.overwriteConfigOff"),
                                    includeContent: t("exchange.importOptions.includeContent"),
                                    includeContentDescription: t("exchange.importOptions.includeContentDescription"),
                                    includeTemplate: t("exchange.importOptions.includeTemplate"),
                                    includeTemplateDescription: t("exchange.importOptions.includeTemplateDescription", { name: zipCheck?.templateName ?? "SPA" }),
                                    templateRequiresStorage: t("exchange.importOptions.templateRequiresStorage"),
                                }}
                            />

                            <DialogFooter>
                                <Button className="flex-1 h-11" variant="outline" onClick={handleReset}>
                                    {t("exchange.importOptions.cancel")}
                                </Button>
                                <GradientButton className="flex-1" onClick={() => file && doImport(file, includeContent, includeTemplate && canIncludeTemplate, overwrite)}>
                                    <IconPackageImport className="size-4" />
                                    {t("exchange.importOptions.importButton")}
                                </GradientButton>
                            </DialogFooter>
                        </div>
                    </div>
                </DialogContent>
            </Dialog>
        </div>
    );
}
