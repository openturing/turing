import { queryKeys } from "@/api/queries/keys"
import {
  useClearStoreCollection,
  useCreateStoreCollection,
  useDeleteStoreCollection,
  useStoreCollectionDocumentCount,
  useStoreCollections,
} from "@/api/queries/store-collection.queries"
import { ROUTES } from "@/app/routes.const"
import { BentoFormSection } from "@/components/bento"
import { DialogDelete } from "@/components/dialog.delete"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { GradientButton } from "@/components/ui/gradient-button"
import { Input } from "@/components/ui/input"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { type TurStoreCollectionInfo, TurStoreInstanceService } from "@/services/store/store.service"
import { useQueries } from "@tanstack/react-query"
import {
  IconDownload,
  IconEraser,
  IconLayersSubtract,
  IconLoader2,
  IconPackageImport,
  IconPlus,
  IconTrash,
} from "@tabler/icons-react"
import { toast } from "@viglet/viglet-design-system"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"

const turStoreInstanceService = new TurStoreInstanceService()

interface Props {
  storeId: string
  readOnly?: boolean
}

function CollectionRow({
  col,
  storeId,
  readOnly,
}: Readonly<{ col: TurStoreCollectionInfo; storeId: string; readOnly: boolean }>) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [clearOpen, setClearOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [exporting, setExporting] = useState(false)
  const clearMutation = useClearStoreCollection()
  const deleteMutation = useDeleteStoreCollection()

  // Backend returns -1 when computing the count is expensive (Chroma scans
  // chunk metadata) — fetch it lazily so the table renders immediately.
  const needsLazyCount = col.documentCount < 0
  const lazyCount = useStoreCollectionDocumentCount(storeId, col.name, needsLazyCount)
  const resolvedDocumentCount = needsLazyCount ? lazyCount.data : col.documentCount
  const showDocumentSpinner = needsLazyCount && lazyCount.isPending

  const handleClear = () => {
    setClearOpen(false)
    clearMutation.mutate(
      { storeId, collectionName: col.name },
      {
        onSuccess: () => toast.success(t("store.collections.cleared", { name: col.name })),
        onError: () => toast.error(t("store.collections.clearFailed", { name: col.name })),
      },
    )
  }

  const handleDelete = () => {
    setDeleteOpen(false)
    deleteMutation.mutate(
      { storeId, collectionName: col.name },
      {
        onSuccess: () => toast.success(t("store.collections.collectionDeleted", { name: col.name })),
        onError: () => toast.error(t("store.collections.deleteFailed", { name: col.name })),
      },
    )
  }

  const handleExport = async () => {
    setExporting(true)
    try {
      const blob = await turStoreInstanceService.exportCollection(storeId, col.name)
      const safeName = col.name.replace(/[^A-Za-z0-9._-]/g, "_")
      const url = globalThis.URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url
      a.download = `${safeName}.jsonl.zip`
      document.body.appendChild(a)
      a.click()
      globalThis.URL.revokeObjectURL(url)
      a.remove()
      toast.success(t("store.collections.exported", { name: col.name }))
    } catch {
      toast.error(t("store.collections.exportFailed", { name: col.name }))
    } finally {
      setExporting(false)
    }
  }

  const renderDocumentCount = () => {
    if (showDocumentSpinner) {
      return <IconLoader2 className="size-4 animate-spin inline-block text-muted-foreground" />
    }
    if (resolvedDocumentCount != null && resolvedDocumentCount >= 0) {
      return resolvedDocumentCount.toLocaleString()
    }
    return <span className="text-muted-foreground">—</span>
  }

  return (
    <TableRow>
      <TableCell className="font-medium">
        <div className="flex items-center gap-2">
          <IconLayersSubtract className="size-4 text-violet-500 shrink-0" />
          {col.name}
        </div>
      </TableCell>
      <TableCell className="text-muted-foreground text-xs font-mono">{col.id}</TableCell>
      <TableCell className="text-right font-medium tabular-nums">{renderDocumentCount()}</TableCell>
      <TableCell className="text-right font-medium tabular-nums">{col.chunkCount.toLocaleString()}</TableCell>
      <TableCell className="text-right">
        <div className="flex items-center justify-end gap-1">
          {!readOnly && (
            <Button
              variant="ghost"
              size="icon-sm"
              title={t("store.collections.import")}
              onClick={() =>
                navigate(
                  `${ROUTES.BENTO_STORE_INSTANCE}/${storeId}/collections/${encodeURIComponent(col.name)}/import`,
                )
              }
            >
              <IconPackageImport className="size-4" />
            </Button>
          )}
          <Button
            variant="ghost"
            size="icon-sm"
            title={t("store.collections.export")}
            onClick={handleExport}
            disabled={exporting}
          >
            <IconDownload className="size-4" />
          </Button>
          {!readOnly && (
            <>
              <DialogDelete
                feature="documents"
                name={col.name}
                open={clearOpen}
                setOpen={setClearOpen}
                onDelete={handleClear}
                trigger={
                  <Button variant="ghost" size="icon-sm" title={t("store.collections.removeAllDocs")}>
                    <IconEraser className="size-4" />
                  </Button>
                }
                title={t("store.collections.removeAllDocs") + "?"}
                description={t("store.collections.removeAllDocsDesc", { name: col.name })}
                confirmLabel={t("store.collections.confirmRemoveAll")}
              />
              <DialogDelete
                feature="collection"
                name={col.name}
                open={deleteOpen}
                setOpen={setDeleteOpen}
                onDelete={handleDelete}
                trigger={
                  <Button variant="ghost" size="icon-sm" title={t("store.collections.deleteCollection")}>
                    <IconTrash className="size-4" />
                  </Button>
                }
              />
            </>
          )}
        </div>
      </TableCell>
    </TableRow>
  )
}

function CreateCollectionDialog({ storeId }: Readonly<{ storeId: string }>) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [name, setName] = useState("")
  const createMutation = useCreateStoreCollection()
  const creating = createMutation.isPending

  const handleCreate = () => {
    const trimmed = name.trim()
    if (!trimmed) return
    createMutation.mutate(
      { storeId, name: trimmed },
      {
        onSuccess: () => {
          toast.success(t("store.collections.created", { name: trimmed }))
          setName("")
          setOpen(false)
        },
        onError: () => toast.error(t("store.collections.createFailed", { name: trimmed })),
      },
    )
  }

  return (
    <>
      <GradientButton variant="outline" size="sm" onClick={() => setOpen(true)}>
        <IconPlus className="size-4" />
        {t("store.collections.newCollection")}
      </GradientButton>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t("store.collections.createCollection")}</DialogTitle>
            <DialogDescription>{t("store.collections.createCollectionDesc")}</DialogDescription>
          </DialogHeader>
          <Input
            placeholder="e.g., vector_store"
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") handleCreate()
            }}
            autoFocus
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              {t("forms.formActions.cancel")}
            </Button>
            <GradientButton onClick={handleCreate} disabled={!name.trim() || creating}>
              {creating ? t("store.collections.creating") : t("store.collections.createCollection")}
            </GradientButton>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

/**
 * Bento collections panel for an Embedding Store detail page. Lists the vector
 * collections stored in the instance (name / id / documents / chunks) with the
 * core create/clear/delete/export/import actions, mirroring the console
 * collections page but rendered as a frosted {@link BentoFormSection}. Read-only
 * for the shared GLOBAL pool (T372).
 */
export function BentoStoreCollections({ storeId, readOnly = false }: Readonly<Props>) {
  const { t } = useTranslation()
  const { data: collections, isError } = useStoreCollections(storeId)

  // Backends like Chroma return -1 for documentCount; rows resolve those lazily
  // via useStoreCollectionDocumentCount. Mirror the same fetches at this level
  // (react-query dedupes by key) so the header total can include them once they
  // land — otherwise it would stay as `—` forever.
  const lazyCollections = (collections ?? []).filter((c) => c.documentCount < 0)
  const lazyCountResults = useQueries({
    queries: lazyCollections.map((c) => ({
      queryKey: queryKeys.storeCollections.documentCount(storeId, c.name),
      queryFn: () => turStoreInstanceService.collectionDocumentCount(storeId, c.name),
      staleTime: 60_000,
    })),
  })

  const eagerDocumentTotal = (collections ?? [])
    .filter((c) => c.documentCount >= 0)
    .reduce((sum, c) => sum + c.documentCount, 0)
  const lazyDocumentTotal = lazyCountResults.reduce(
    (sum, r) => sum + (typeof r.data === "number" && r.data >= 0 ? r.data : 0),
    0,
  )
  const hasUnknownDocumentCounts = lazyCountResults.some(
    (r) => !(typeof r.data === "number" && r.data >= 0),
  )
  const totalDocuments = eagerDocumentTotal + lazyDocumentTotal
  const totalChunks = collections?.reduce((sum, c) => sum + c.chunkCount, 0) ?? 0

  const stat = (label: string, value: string | number) => (
    <div className="rounded-xl bg-muted/40 px-3 py-1.5 text-center">
      <span className="block text-[10px] uppercase tracking-wider text-muted-foreground">{label}</span>
      <span className="text-sm font-semibold tabular-nums">{value}</span>
    </div>
  )

  return (
    <BentoFormSection
      icon={IconLayersSubtract}
      tone="violet"
      title={t("store.collections.title")}
      description={t("store.collections.description")}
      trailing={!readOnly ? <CreateCollectionDialog storeId={storeId} /> : undefined}
    >
      {isError ? (
        <p className="text-sm text-destructive">{t("store.collections.loadFailed")}</p>
      ) : (
        <>
          {collections && collections.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {stat(t("store.collections.title"), collections.length.toLocaleString())}
              {stat(
                t("store.collections.totalDocs"),
                hasUnknownDocumentCounts ? "—" : totalDocuments.toLocaleString(),
              )}
              {stat(t("store.collections.totalChunks"), totalChunks.toLocaleString())}
            </div>
          )}

          {collections && collections.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">
              {t("store.collections.blankTitle")}
            </p>
          ) : (
            <div className="rounded-2xl border border-border/60 overflow-hidden">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("store.collections.name")}</TableHead>
                    <TableHead className="w-50">{t("store.collections.id")}</TableHead>
                    <TableHead className="w-30 text-right">{t("store.collections.documents")}</TableHead>
                    <TableHead className="w-30 text-right">{t("store.collections.chunks")}</TableHead>
                    <TableHead className="w-35 text-right" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {collections
                    ?.slice()
                    .sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: "base" }))
                    .map((col) => (
                      <CollectionRow key={col.id} col={col} storeId={storeId} readOnly={readOnly} />
                    ))}
                </TableBody>
              </Table>
            </div>
          )}
        </>
      )}
    </BentoFormSection>
  )
}
