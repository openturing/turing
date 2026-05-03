import { useDeleteStoreInstance } from "@/api/queries/store-instance.queries"
import { ROUTES } from "@/app/routes.const"
import { LoadProvider } from "@/components/loading-provider"
import { SubPage } from "@/components/sub.page"
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import type { TurStoreInstance } from "@/models/store/store-instance.model"
import { TurStoreInstanceService } from "@/services/store/store.service"
import { IconDatabase, IconInfoCircle, IconLayersSubtract, IconSettings } from "@tabler/icons-react"
import { useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"

const turStoreInstanceService = new TurStoreInstanceService()

export default function StoreInstancePage() {
  const { t } = useTranslation()
  const { id } = useParams() as { id: string }
  const [storeInstance, setStoreInstance] = useState<TurStoreInstance>({} as TurStoreInstance)
  const [isNew, setIsNew] = useState(true)
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>(
    id === "new" ? [{ label: t("store.newStore") }] : [{ label: "…" }]
  )
  const navigate = useNavigate()
  const urlBase = `${ROUTES.STORE_INSTANCE}/${id}`
  const deleteMutation = useDeleteStoreInstance()

  const data = useMemo(() => {
    if (isNew) {
      return {
        navMain: [
          { title: t("store.nav.settings"), url: "/detail", icon: IconSettings },
        ],
      }
    }
    return {
      navMain: [
        { title: t("store.nav.settings"), url: "/detail", icon: IconSettings },
        { title: t("store.nav.collections"), url: "/collections", icon: IconLayersSubtract },
        { title: t("store.nav.systemInfo"), url: "/system-info", icon: IconInfoCircle },
      ],
    }
  }, [isNew, t])

  useEffect(() => {
    if (id === "new") {
      setStoreInstance({} as TurStoreInstance)
      setIsNew(true)
    } else {
      turStoreInstanceService.get(id).then((instance) => {
        setStoreInstance(instance)
        setBreadcrumb([{ label: instance.title, href: `${ROUTES.STORE_INSTANCE}/${instance.id}` }])
      }).catch(() => setError(t("common.connectionError", { resource: "Embedding Store instance" })))
      setIsNew(false)
    }
  }, [id])

  useSubPageBreadcrumb(breadcrumb)

  async function onDelete() {
    try {
      if (await deleteMutation.mutateAsync(storeInstance)) {
        toast.success(t("store.deleted", { name: storeInstance.title }))
        navigate(ROUTES.STORE_INSTANCE)
      } else {
        toast.error(t("store.notDeleted", { name: storeInstance.title }))
      }
    } catch (error) {
      console.error("Delete error", error)
      toast.error(t("store.notDeleted", { name: storeInstance.title }))
    }
    setOpen(false)
  }

  return (
    <LoadProvider checkIsNotUndefined={storeInstance} error={error} tryAgainUrl={`${ROUTES.STORE_INSTANCE}/${id}`}>
      <SubPage icon={IconDatabase} feature={t("store.title")} name={storeInstance.title}
        onDelete={onDelete} data={data} isNew={isNew} urlBase={urlBase} open={open} setOpen={setOpen} />
    </LoadProvider>
  )
}
