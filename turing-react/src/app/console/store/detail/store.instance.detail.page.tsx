import { ROUTES } from "@/app/routes.const"
import { LoadProvider } from "@/components/loading-provider"
import { StoreInstanceForm } from "@/components/store/store.instance.form"
import type { TurStoreInstance } from "@/models/store/store-instance.model"
import { TurStoreInstanceService } from "@/services/store/store.service"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useParams } from "react-router-dom"

const turStoreInstanceService = new TurStoreInstanceService()

export default function StoreInstanceDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams() as { id: string }
  const [storeInstance, setStoreInstance] = useState<TurStoreInstance>()
  const [isNew, setIsNew] = useState(true)
  const [error, setError] = useState<string | null>(null)
  useSubPageBreadcrumb(t("store.detail.title"))

  useEffect(() => {
    if (id === "new") {
      setStoreInstance({} as TurStoreInstance)
    } else {
      turStoreInstanceService.get(id)
        .then(setStoreInstance)
        .catch(() => setError(t("common.connectionError", { resource: "Embedding Store instance" })))
      setIsNew(false)
    }
  }, [id])

  return (
    <LoadProvider checkIsNotUndefined={storeInstance} error={error} tryAgainUrl={`${ROUTES.STORE_INSTANCE}/${id}`}>
      {storeInstance && <StoreInstanceForm value={storeInstance} isNew={isNew} />}
    </LoadProvider>
  )
}
