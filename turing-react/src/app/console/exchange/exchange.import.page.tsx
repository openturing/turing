import { ROUTES } from "@/app/routes.const";
import { Card, CardContent } from "@/components/ui/card";
import { TurExchangeImportService } from "@/services/exchange/exchange.import.service";
import {
    IconCheck,
    IconCloudUpload,
    IconFileZip,
    IconLoader2,
    IconX,
} from "@tabler/icons-react";
import { useCallback, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";

const importService = new TurExchangeImportService();

type ImportStatus = "idle" | "uploading" | "success" | "error";

export default function ExchangeImportPage() {
    const [isDragOver, setIsDragOver] = useState(false);
    const [file, setFile] = useState<File | null>(null);
    const [status, setStatus] = useState<ImportStatus>("idle");
    const [progress, setProgress] = useState(0);
    const [errorMessage, setErrorMessage] = useState<string>("");
    const fileInputRef = useRef<HTMLInputElement>(null);
    const navigate = useNavigate();

    const isValidFile = (f: File): boolean => {
        const validTypes = [
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream",
        ];
        return validTypes.includes(f.type) || f.name.endsWith(".zip");
    };

    const handleFile = useCallback(
        async (selectedFile: File) => {
            if (!isValidFile(selectedFile)) {
                setErrorMessage("Please select a valid ZIP file.");
                setStatus("error");
                toast.error("Invalid file type. Only ZIP files are accepted.");
                return;
            }

            setFile(selectedFile);
            setStatus("uploading");
            setProgress(0);
            setErrorMessage("");

            try {
                await importService.importFile(selectedFile, (p) => setProgress(p));
                setStatus("success");
                setProgress(100);
                toast.success("Import completed successfully!");
                setTimeout(() => {
                    navigate(ROUTES.SN_INSTANCE);
                }, 2000);
            } catch (err: any) {
                const msg =
                    err?.response?.data?.message ||
                    err?.message ||
                    "An error occurred during import.";
                setErrorMessage(msg);
                setStatus("error");
                toast.error(`Import failed: ${msg}`);
            }
        },
        [navigate]
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
        if (fileInputRef.current) {
            fileInputRef.current.value = "";
        }
    }, []);

    const formatFileSize = (bytes: number): string => {
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`;
        return `${(bytes / 1048576).toFixed(1)} MB`;
    };

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
                    {/* Progress bar background */}
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
                            aria-label="Select ZIP file to import"
                        />

                        {/* Icon */}
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

                        {/* Text */}
                        <div className="text-center space-y-2">
                            {status === "idle" && (
                                <>
                                    <p className="text-lg font-semibold">
                                        {isDragOver
                                            ? "Drop your file here"
                                            : "Drag & drop your export file"}
                                    </p>
                                    <p className="text-sm text-muted-foreground">
                                        or{" "}
                                        <span className="text-blue-500 font-medium underline underline-offset-4 decoration-blue-500/30 hover:decoration-blue-500">
                                            browse files
                                        </span>{" "}
                                        to select a ZIP archive
                                    </p>
                                    <p className="text-xs text-muted-foreground/60 pt-1">
                                        Only .zip files exported from Turing are supported
                                    </p>
                                </>
                            )}

                            {status === "uploading" && (
                                <>
                                    <p className="text-lg font-semibold text-blue-500">
                                        Importing...
                                    </p>
                                    <p className="text-sm text-muted-foreground">
                                        {progress}% uploaded
                                    </p>
                                </>
                            )}

                            {status === "success" && (
                                <>
                                    <p className="text-lg font-semibold text-emerald-500">
                                        Import Completed
                                    </p>
                                    <p className="text-sm text-muted-foreground">
                                        Redirecting to Semantic Navigation...
                                    </p>
                                </>
                            )}

                            {status === "error" && (
                                <>
                                    <p className="text-lg font-semibold text-red-500">
                                        Import Failed
                                    </p>
                                    <p className="text-sm text-muted-foreground">
                                        {errorMessage}
                                    </p>
                                    <button
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
                {file && status !== "idle" && (
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
        </div>
    );
}
