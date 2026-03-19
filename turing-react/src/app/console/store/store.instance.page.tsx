import { ROUTES } from "@/app/routes.const"
import { LoadProvider } from "@/components/loading-provider"
import { SubPage } from "@/components/sub.page"
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import type { TurStoreInstance } from "@/models/store/store-instance.model"
import { TurStoreInstanceService } from "@/services/store/store.service"
import { IconDatabase, IconInfoCircle, IconLayersSubtract, IconSettings } from "@tabler/icons-react"
import { useEffect, useMemo, useState } from "react"
import { useNavigate, useParams } from "react-router-dom"
import { toast } from "sonner"

const turStoreInstanceService = new TurStoreInstanceService()

export default function StoreInstancePage() {
  const { id } = useParams() as { id: string }
  const [storeInstance, setStoreInstance] = useState<TurStoreInstance>({} as TurStoreInstance)
  const [isNew, setIsNew] = useState(true)
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>()
  const navigate = useNavigate()
  const urlBase = `${ROUTES.STORE_INSTANCE}/${id}`

  const data = useMemo(() => {
    if (isNew) {
      return {
        navMain: [
          { title: "Settings", url: "/detail", icon: IconSettings },
        ],
      }
    }
    return {
      navMain: [
        { title: "Settings", url: "/detail", icon: IconSettings },
        { title: "Collections", url: "/collections", icon: IconLayersSubtract },
        { title: "System Information", url: "/system-info", icon: IconInfoCircle },
      ],
    }
  }, [isNew])

  useEffect(() => {
    if (id === "new") {
      setStoreInstance({} as TurStoreInstance)
      setIsNew(true)
      setBreadcrumb([{ label: "New Embedding Store" }])
    } else {
      turStoreInstanceService.get(id).then((instance) => {
        setStoreInstance(instance)
        setBreadcrumb([{ label: instance.title, href: `${ROUTES.STORE_INSTANCE}/${instance.id}` }])
      }).catch(() => setError("Connection error or timeout while fetching Embedding Store instance."))
      setIsNew(false)
    }
  }, [id])

  useSubPageBreadcrumb(breadcrumb)

  async function onDelete() {
    try {
      if (await turStoreInstanceService.delete(storeInstance)) {
        toast.success(`The ${storeInstance.title} Embedding Store was deleted`)
        navigate(ROUTES.STORE_INSTANCE)
      } else {
        toast.error(`The ${storeInstance.title} Embedding Store was not deleted`)
      }
    } catch (error) {
      console.error("Delete error", error)
      toast.error(`The ${storeInstance.title} Embedding Store was not deleted`)
    }
    setOpen(false)
  }

  return (
    <LoadProvider checkIsNotUndefined={storeInstance} error={error} tryAgainUrl={`${ROUTES.STORE_INSTANCE}/${id}`}>
      <SubPage icon={IconDatabase} feature="Embedding Store" name={storeInstance.title}
        onDelete={onDelete} data={data} isNew={isNew} urlBase={urlBase} open={open} setOpen={setOpen} />
    </LoadProvider>
  )
}
