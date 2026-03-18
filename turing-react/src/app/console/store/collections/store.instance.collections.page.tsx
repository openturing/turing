import { DialogDelete } from "@/components/dialog.delete"
import { LoadProvider } from "@/components/loading-provider"
import { SubPageHeader } from "@/components/sub.page.header"
import { Button } from "@/components/ui/button"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import { type TurStoreCollectionInfo, TurStoreInstanceService } from "@/services/store/store.service"
import { IconDatabase, IconEraser, IconLayersSubtract, IconTrash } from "@tabler/icons-react"
import { useCallback, useEffect, useState } from "react"
import { useParams } from "react-router-dom"
import { toast } from "sonner"

const turStoreInstanceService = new TurStoreInstanceService()

function CollectionRow({ col, storeId, onRefresh }: Readonly<{ col: TurStoreCollectionInfo; storeId: string; onRefresh: () => void }>) {
  const [clearOpen, setClearOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)

  const handleClear = () => {
    setClearOpen(false)
    turStoreInstanceService.clearCollection(storeId, col.name)
      .then(() => { toast.success(`Collection "${col.name}" cleared.`); onRefresh() })
      .catch(() => toast.error(`Failed to clear collection "${col.name}".`))
  }

  const handleDelete = () => {
    setDeleteOpen(false)
    turStoreInstanceService.deleteCollection(storeId, col.name)
      .then(() => { toast.success(`Collection "${col.name}" deleted.`); onRefresh() })
      .catch(() => toast.error(`Failed to delete collection "${col.name}".`))
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
      <TableCell className="text-right">
        <div className="flex items-center justify-end gap-1">
          <DialogDelete
            feature="documents"
            name={col.name}
            open={clearOpen}
            setOpen={setClearOpen}
            onDelete={handleClear}
            trigger={
              <Button variant="ghost" size="icon-sm" title="Remove all documents">
                <IconEraser className="size-4" />
              </Button>
            }
            title="Remove all documents?"
            description={`This will delete all embeddings from the "${col.name}" collection. The collection itself will be recreated empty.`}
            confirmLabel="Yes, remove all documents"
          />
          <DialogDelete
            feature="collection"
            name={col.name}
            open={deleteOpen}
            setOpen={setDeleteOpen}
            onDelete={handleDelete}
            trigger={
              <Button variant="ghost" size="icon-sm" title="Delete collection">
                <IconTrash className="size-4" />
              </Button>
            }
          />
        </div>
      </TableCell>
    </TableRow>
  )
}

export default function StoreInstanceCollectionsPage() {
  const { id } = useParams() as { id: string }
  const [collections, setCollections] = useState<TurStoreCollectionInfo[]>()
  const [error, setError] = useState<string | null>(null)
  useSubPageBreadcrumb("Collections")

  const loadCollections = useCallback(() => {
    turStoreInstanceService.collections(id)
      .then(setCollections)
      .catch(() => setError("Failed to load collections. Check the Embedding Store connection."))
  }, [id])

  useEffect(() => { loadCollections() }, [loadCollections])

  const totalDocuments = collections?.reduce((sum, c) => sum + c.documentCount, 0) ?? 0

  return (
    <LoadProvider checkIsNotUndefined={collections} error={error}>
      <SubPageHeader icon={IconLayersSubtract} feature="Embedding Store" name="Collections"
        description="Vector collections stored in this instance." />

      <div className="px-4 lg:px-6 space-y-4">
        {collections && collections.length > 0 && (
          <div className="flex gap-4">
            <div className="rounded-lg bg-muted/40 px-4 py-3">
              <span className="text-xs text-muted-foreground">Collections</span>
              <p className="text-lg font-semibold">{collections.length}</p>
            </div>
            <div className="rounded-lg bg-muted/40 px-4 py-3">
              <span className="text-xs text-muted-foreground">Total Documents</span>
              <p className="text-lg font-semibold">{totalDocuments.toLocaleString()}</p>
            </div>
          </div>
        )}

        {collections && collections.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
            <IconDatabase className="size-12 mb-3 opacity-40" />
            <p className="text-sm">No collections found in this store.</p>
          </div>
        ) : (
          <div className="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead className="w-[200px]">ID</TableHead>
                  <TableHead className="w-[150px] text-right">Documents</TableHead>
                  <TableHead className="w-[100px] text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {collections?.map((col) => (
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
