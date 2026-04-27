import { ROUTES } from "@/app/routes.const"
import { SubPageHeader } from "@/components/sub.page.header"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { FloatingFormulasBg } from "@/components/ui/floating-formulas-bg"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import {
  type TurStoreImportResult,
  TurStoreInstanceService,
} from "@/services/store/store.service"
import {
  IconArrowLeft,
  IconCheck,
  IconCloudUpload,
  IconFileText,
  IconLoader2,
  IconPackageImport,
  IconX,
} from "@tabler/icons-react"
import { useCallback, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"

const turStoreInstanceService = new TurStoreInstanceService()

type ImportStatus = "idle" | "uploading" | "success" | "error"

const ACCEPTED_EXTENSIONS = [".jsonl", ".ndjson", ".zip"]

function isAcceptedFile(file: File): boolean {
  const lower = file.name.toLowerCase()
  return ACCEPTED_EXTENSIONS.some((ext) => lower.endsWith(ext))
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1048576).toFixed(1)} MB`
}

export default function StoreInstanceCollectionImportPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { id, collectionName } = useParams() as { id: string; collectionName: string }

  const [isDragOver, setIsDragOver] = useState(false)
  const [file, setFile] = useState<File | null>(null)
  const [status, setStatus] = useState<ImportStatus>("idle")
  const [progress, setProgress] = useState(0)
  const [errorMessage, setErrorMessage] = useState("")
  const [result, setResult] = useState<TurStoreImportResult | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useSubPageBreadcrumb(t("store.collections.importTitle", { name: collectionName }))

  const collectionsHref = `${ROUTES.STORE_INSTANCE}/${id}/collections`

  const doImport = useCallback(
    async (selected: File) => {
      setStatus("uploading")
      setProgress(0)
      setErrorMessage("")
      setResult(null)
      try {
        const r = await turStoreInstanceService.importCollection(
          id,
          collectionName,
          selected,
          (p) => setProgress(p),
        )
        setResult(r)
        setStatus("success")
        setProgress(100)
        toast.success(t("store.collections.importCompleted", { name: collectionName }))
      } catch (err: unknown) {
        const error = err as { response?: { data?: { message?: string } }; message?: string }
        const msg =
          error?.response?.data?.message ?? error?.message ?? t("store.collections.importFailed", { name: collectionName })
        setErrorMessage(msg)
        setStatus("error")
        toast.error(msg)
      }
    },
    [collectionName, id, t],
  )

  const handleFile = useCallback(
    (selected: File) => {
      if (!isAcceptedFile(selected)) {
        setErrorMessage(t("store.collections.importInvalidType"))
        setStatus("error")
        toast.error(t("store.collections.importInvalidType"))
        return
      }
      setFile(selected)
      doImport(selected)
    },
    [doImport, t],
  )

  const handleDragOver = useCallback(
    (e: React.DragEvent<HTMLDivElement>) => {
      e.preventDefault()
      e.stopPropagation()
      if (status !== "uploading") setIsDragOver(true)
    },
    [status],
  )

  const handleDragLeave = useCallback((e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    e.stopPropagation()
    setIsDragOver(false)
  }, [])

  const handleDrop = useCallback(
    (e: React.DragEvent<HTMLDivElement>) => {
      e.preventDefault()
      e.stopPropagation()
      setIsDragOver(false)
      if (status === "uploading") return
      const dropped = e.dataTransfer.files?.[0]
      if (dropped) handleFile(dropped)
    },
    [handleFile, status],
  )

  const handleClick = useCallback(() => {
    if (status !== "uploading") {
      fileInputRef.current?.click()
    }
  }, [status])

  const handleFileChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const selected = e.target.files?.[0]
      if (selected) handleFile(selected)
    },
    [handleFile],
  )

  const handleReset = useCallback(() => {
    setFile(null)
    setStatus("idle")
    setProgress(0)
    setErrorMessage("")
    setResult(null)
    if (fileInputRef.current) fileInputRef.current.value = ""
  }, [])

  return (
    <>
      <SubPageHeader
        icon={IconPackageImport}
        feature={t("store.title")}
        name={t("store.collections.importTitle", { name: collectionName })}
        description={t("store.collections.importDescription")}
      />

      <div className="flex flex-col items-center px-4 md:px-6 lg:px-8 py-2">
        <div className="w-full max-w-2xl space-y-6">
          <Card
            className={`
              relative overflow-hidden transition-all duration-300 ease-out
              ${status === "uploading" ? "cursor-wait" : "cursor-pointer"}
              ${
                isDragOver
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
                accept=".jsonl,.ndjson,.zip,application/zip,application/x-ndjson"
                onChange={handleFileChange}
                className="hidden"
                aria-label={t("store.collections.importBrowse")}
              />

              <div
                className={`
                  rounded-2xl p-4 transition-all duration-300
                  ${
                    isDragOver
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
                        ? t("store.collections.importDropFile")
                        : t("store.collections.importDragDrop")}
                    </p>
                    <p className="text-sm text-muted-foreground">
                      <span className="text-blue-500 font-medium underline underline-offset-4 decoration-blue-500/30 hover:decoration-blue-500">
                        {t("store.collections.importBrowse")}
                      </span>{" "}
                      {t("store.collections.importAccepted")}
                    </p>
                    <p className="text-xs text-muted-foreground/60 pt-1">
                      {t("store.collections.importHint")}
                    </p>
                  </>
                )}

                {status === "uploading" && (
                  <>
                    <p className="text-lg font-semibold text-blue-500">
                      {t("store.collections.importUploading")}
                    </p>
                    <p className="text-sm text-muted-foreground">
                      {progress}% {t("store.collections.importUploaded")}
                    </p>
                  </>
                )}

                {status === "success" && result && (
                  <div className="w-full max-w-sm">
                    <ImportResultBlock result={result} />
                  </div>
                )}

                {status === "error" && (
                  <>
                    <p className="text-lg font-semibold text-red-500">
                      {t("store.collections.importFailedTitle")}
                    </p>
                    <p className="text-sm text-muted-foreground">{errorMessage}</p>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation()
                        handleReset()
                      }}
                      className="mt-2 text-sm text-blue-500 font-medium underline underline-offset-4 decoration-blue-500/30 hover:decoration-blue-500 cursor-pointer"
                    >
                      {t("store.collections.importTryAgain")}
                    </button>
                  </>
                )}
              </div>
            </CardContent>
          </Card>

          {file && status !== "idle" && (
            <Card className="transition-all duration-300 animate-in fade-in-0 slide-in-from-bottom-2">
              <CardContent className="flex items-center gap-4 py-4">
                <div
                  className={`
                    rounded-xl p-2.5 shrink-0
                    ${
                      status === "success"
                        ? "bg-emerald-500/10 text-emerald-500"
                        : status === "error"
                          ? "bg-red-500/10 text-red-500"
                          : "bg-blue-500/10 text-blue-500"
                    }
                  `}
                >
                  <IconFileText className="size-5" />
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
                      e.stopPropagation()
                      handleReset()
                    }}
                    title={t("store.collections.importDismiss")}
                    className="shrink-0 rounded-lg p-1.5 hover:bg-muted transition-colors cursor-pointer"
                  >
                    <IconX className="size-4 text-muted-foreground" />
                  </button>
                )}
              </CardContent>
            </Card>
          )}

          <div className="flex items-center justify-between">
            <Button variant="outline" onClick={() => navigate(collectionsHref)}>
              <IconArrowLeft className="size-4" />
              {t("store.collections.importBackToCollections")}
            </Button>
            {status === "success" && (
              <Button variant="outline" onClick={handleReset}>
                {t("store.collections.importAgain")}
              </Button>
            )}
          </div>
        </div>
      </div>
    </>
  )
}

function ImportResultBlock({ result }: Readonly<{ result: TurStoreImportResult }>) {
  const { t } = useTranslation()
  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2 rounded-lg bg-emerald-500/10 border border-emerald-500/30 px-3 py-2.5 text-emerald-600 dark:text-emerald-400">
        <IconCheck className="size-4" />
        <span className="text-sm font-medium">
          {t("store.collections.importDoneShort", {
            imported: result.imported,
            total: result.total,
          })}
        </span>
      </div>
      <div className="rounded-lg border border-border/60 divide-y divide-border/60 text-sm">
        <div className="flex items-center justify-between px-3 py-2">
          <span className="text-muted-foreground">{t("store.collections.importStatTotal")}</span>
          <span className="font-medium tabular-nums">{result.total}</span>
        </div>
        <div className="flex items-center justify-between px-3 py-2">
          <span className="text-muted-foreground">
            {t("store.collections.importStatPreserved")}
          </span>
          <span className="font-medium tabular-nums">{result.preserved}</span>
        </div>
        <div className="flex items-center justify-between px-3 py-2">
          <span className="text-muted-foreground">
            {t("store.collections.importStatReEmbedded")}
          </span>
          <span className="font-medium tabular-nums">{result.reEmbedded}</span>
        </div>
        {result.failed > 0 && (
          <div className="flex items-center justify-between px-3 py-2 text-red-600 dark:text-red-400">
            <span className="text-muted-foreground">
              {t("store.collections.importStatFailed")}
            </span>
            <span className="font-medium tabular-nums">{result.failed}</span>
          </div>
        )}
      </div>
    </div>
  )
}
