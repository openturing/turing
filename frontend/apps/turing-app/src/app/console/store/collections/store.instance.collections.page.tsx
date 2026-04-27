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
import { IconDatabase, IconDownload, IconEraser, IconLayersSubtract, IconPackageImport, IconPlus, IconTrash } from "@tabler/icons-react"
import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"

const turStoreInstanceService = new TurStoreInstanceService()

function CollectionRow({ col, storeId, onRefresh }: Readonly<{ col: TurStoreCollectionInfo; storeId: string; onRefresh: () => void }>) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [clearOpen, setClearOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [exporting, setExporting] = useState(false)

  const handleClear = () => {
    setClearOpen(false)
    turStoreInstanceService.clearCollection(storeId, col.name)
      .then(() => { toast.success(t("store.collections.cleared", { name: col.name })); onRefresh() })
      .catch(() => toast.error(t("store.collections.clearFailed", { name: col.name })))
  }

  const handleDelete = () => {
    setDeleteOpen(false)
    turStoreInstanceService.deleteCollection(storeId, col.name)
      .then(() => { toast.success(t("store.collections.collectionDeleted", { name: col.name })); onRefresh() })
      .catch(() => toast.error(t("store.collections.deleteFailed", { name: col.name })))
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
      <TableCell className="text-right font-medium tabular-nums">{col.documentCount.toLocaleString()}</TableCell>
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

function CreateCollectionDialog({ storeId, onCreated }: Readonly<{ storeId: string; onCreated: () => void }>) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [name, setName] = useState("")
  const [creating, setCreating] = useState(false)

  const handleCreate = () => {
    const trimmed = name.trim()
    if (!trimmed) return
    setCreating(true)
    turStoreInstanceService.createCollection(storeId, trimmed)
      .then(() => {
        toast.success(t("store.collections.created", { name: trimmed }))
        setName("")
        setOpen(false)
        onCreated()
      })
      .catch(() => toast.error(t("store.collections.createFailed", { name: trimmed })))
      .finally(() => setCreating(false))
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
  const [collections, setCollections] = useState<TurStoreCollectionInfo[]>()
  const [error, setError] = useState<string | null>(null)
  useSubPageBreadcrumb(t("store.collections.title"))

  const loadCollections = useCallback(() => {
    turStoreInstanceService.collections(id)
      .then(setCollections)
      .catch(() => setError(t("store.collections.loadFailed")))
  }, [id])

  useEffect(() => { loadCollections() }, [loadCollections])

  const totalDocuments = collections?.reduce((sum, c) => sum + c.documentCount, 0) ?? 0
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
                  <p className="text-lg font-semibold">{totalDocuments.toLocaleString()}</p>
                </div>
                <div className="rounded-lg bg-muted/40 px-4 py-3">
                  <span className="text-xs text-muted-foreground">{t("store.collections.totalChunks")}</span>
                  <p className="text-lg font-semibold">{totalChunks.toLocaleString()}</p>
                </div>
              </>
            )}
          </div>
          <CreateCollectionDialog storeId={id} onCreated={loadCollections} />
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
                    <CollectionRow key={col.id} col={col} storeId={id} onRefresh={loadCollections} />
                  ))}
              </TableBody>
            </Table>
          </div>
        )}
      </div>
    </LoadProvider>
  )
}
