import { DialogDelete } from "@/components/dialog.delete"
import { LoadProvider } from "@/components/loading-provider"
import { SubPageHeader } from "@/components/sub.page.header"
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
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import { ROUTES } from "@/app/routes.const"
import { type TurStoreCollectionInfo, TurStoreInstanceService } from "@/services/store/store.service"
import {
  useClearStoreCollection,
  useCreateStoreCollection,
  useDeleteStoreCollection,
  useStoreCollectionDocumentCount,
  useStoreCollections,
} from "@/api/queries/store-collection.queries"
import { IconDatabase, IconDownload, IconEraser, IconLayersSubtract, IconLoader2, IconPackageImport, IconPlus, IconTrash } from "@tabler/icons-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"

const turStoreInstanceService = new TurStoreInstanceService()

function CollectionRow({ col, storeId }: Readonly<{ col: TurStoreCollectionInfo; storeId: string }>) {
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

  return (
    <TableRow>
      <TableCell className="font-medium">
        <div className="flex items-center gap-2">
          <IconLayersSubtract className="size-4 text-blue-500 shrink-0" />
          {col.name}
        </div>
      </TableCell>
      <TableCell className="text-muted-foreground text-xs font-mono">{col.id}</TableCell>
      <TableCell className="text-right font-medium tabular-nums">
        {showDocumentSpinner ? (
          <IconLoader2 className="size-4 animate-spin inline-block text-muted-foreground" />
        ) : resolvedDocumentCount != null && resolvedDocumentCount >= 0 ? (
          resolvedDocumentCount.toLocaleString()
        ) : (
          <span className="text-muted-foreground">—</span>
        )}
      </TableCell>
      <TableCell className="text-right font-medium tabular-nums">{col.chunkCount.toLocaleString()}</TableCell>
      <TableCell className="text-right">
        <div className="flex items-center justify-end gap-1">
          <Button
            variant="ghost"
            size="icon-sm"
            title={t("store.collections.import")}
            onClick={() =>
              navigate(
                `${ROUTES.STORE_INSTANCE}/${storeId}/collections/${encodeURIComponent(col.name)}/import`,
              )
            }
          >
            <IconPackageImport className="size-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon-sm"
            title={t("store.collections.export")}
            onClick={handleExport}
            disabled={exporting}
          >
            <IconDownload className="size-4" />
          </Button>
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
      <GradientButton variant="outline" onClick={() => setOpen(true)}>
        <IconPlus className="size-4" />
        {t("store.collections.newCollection")}
      </GradientButton>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t("store.collections.createCollection")}</DialogTitle>
            <DialogDescription>
              {t("store.collections.createCollectionDesc")}
            </DialogDescription>
          </DialogHeader>
          <Input
            placeholder="e.g., vector_store"
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter") handleCreate() }}
            autoFocus
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
            <GradientButton onClick={handleCreate} disabled={!name.trim() || creating}>
              {creating ? t("store.collections.creating") : t("store.collections.createCollection")}
            </GradientButton>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

export default function StoreInstanceCollectionsPage() {
  const { t } = useTranslation()
  const { id } = useParams() as { id: string }
  useSubPageBreadcrumb(t("store.collections.title"))

  const { data: collections, isError } = useStoreCollections(id)
  const error = isError ? t("store.collections.loadFailed") : null

  // Skip rows where documentCount is the -1 sentinel (lazy-loaded per row).
  // The total stays accurate for backends that compute it eagerly (PG, Lucene)
  // and is omitted when any Chroma row hasn't resolved its count yet.
  const knownDocumentCounts = collections?.filter((c) => c.documentCount >= 0) ?? []
  const hasUnknownDocumentCounts = (collections?.length ?? 0) > knownDocumentCounts.length
  const totalDocuments = knownDocumentCounts.reduce((sum, c) => sum + c.documentCount, 0)
  const totalChunks = collections?.reduce((sum, c) => sum + c.chunkCount, 0) ?? 0

  return (
    <LoadProvider checkIsNotUndefined={collections} error={error}>
      <SubPageHeader icon={IconLayersSubtract} feature={t("store.title")} name={t("store.collections.title")}
        description={t("store.collections.description")} />

      <div className="px-4 lg:px-6 space-y-4">
        <div className="flex items-center justify-between gap-4">
          <div className="flex gap-4">
            {collections && collections.length > 0 && (
              <>
                <div className="rounded-lg bg-muted/40 px-4 py-3">
                  <span className="text-xs text-muted-foreground">{t("store.collections.title")}</span>
                  <p className="text-lg font-semibold">{collections.length}</p>
                </div>
                <div className="rounded-lg bg-muted/40 px-4 py-3">
                  <span className="text-xs text-muted-foreground">{t("store.collections.totalDocs")}</span>
                  <p className="text-lg font-semibold">
                    {hasUnknownDocumentCounts ? (
                      <span className="text-muted-foreground">—</span>
                    ) : (
                      totalDocuments.toLocaleString()
                    )}
                  </p>
                </div>
                <div className="rounded-lg bg-muted/40 px-4 py-3">
                  <span className="text-xs text-muted-foreground">{t("store.collections.totalChunks")}</span>
                  <p className="text-lg font-semibold">{totalChunks.toLocaleString()}</p>
                </div>
              </>
            )}
          </div>
          <CreateCollectionDialog storeId={id} />
        </div>

        {collections && collections.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
            <IconDatabase className="size-12 mb-3 opacity-40" />
            <p className="text-sm">{t("store.collections.blankTitle")}</p>
          </div>
        ) : (
          <div className="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("store.collections.name")}</TableHead>
                  <TableHead className="w-[200px]">{t("store.collections.id")}</TableHead>
                  <TableHead className="w-[120px] text-right">{t("store.collections.documents")}</TableHead>
                  <TableHead className="w-[120px] text-right">{t("store.collections.chunks")}</TableHead>
                  <TableHead className="w-[100px] text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {collections
                  ?.slice()
                  .sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: "base" }))
                  .map((col) => (
                    <CollectionRow key={col.id} col={col} storeId={id} />
                  ))}
              </TableBody>
            </Table>
          </div>
        )}
      </div>
    </LoadProvider>
  )
}
